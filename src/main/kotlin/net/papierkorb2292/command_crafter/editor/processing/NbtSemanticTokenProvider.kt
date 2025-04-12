package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import net.minecraft.nbt.AbstractNbtList
import net.minecraft.nbt.AbstractNbtNumber
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtString

class NbtSemanticTokenProvider(val tree: StringRangeTree<NbtElement>, val input: String) : StringRangeTree.SemanticTokenProvider<NbtElement> {
    override fun getMapNameTokenInfo(map: NbtElement) = StringRangeTree.TokenInfo(TokenType.PROPERTY, 0)

    override fun getNodeTokenInfo(node: NbtElement) = when(node) {
        is NbtString -> StringRangeTree.TokenInfo(TokenType.STRING, 0)
        is AbstractNbtNumber -> {
            val startChar = input[tree.ranges[node]!!.start]
            if(startChar == 't' || startChar == 'f')
                // Number is a boolean
                StringRangeTree.TokenInfo(TokenType.ENUM_MEMBER, 0)
            else
                StringRangeTree.TokenInfo(TokenType.NUMBER, 0)
        }
        else -> null
    }

    override fun getAdditionalTokens(node: NbtElement) = when(node) {
        is AbstractNbtList -> {
            val nodeStart = tree.ranges[node]!!.start
            if(input.length > nodeStart + 2 && !StringReader.isQuotedStringStart(input[nodeStart + 1]) && input[nodeStart + 2] == ';')
                // Is a primitive array
                listOf(
                    StringRangeTree.AdditionalToken(
                        StringRange(nodeStart + 1, nodeStart + 2),
                        StringRangeTree.TokenInfo(TokenType.TYPE, 0)
                    )
                )
            else
                emptyList()
        }
        else -> emptyList()
    }
}