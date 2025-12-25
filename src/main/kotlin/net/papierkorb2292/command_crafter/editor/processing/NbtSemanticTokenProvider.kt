package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.StringRange
import net.minecraft.nbt.CollectionTag
import net.minecraft.nbt.NumericTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.StringTag

class NbtSemanticTokenProvider(val tree: StringRangeTree<Tag>, val input: String) : StringRangeTree.SemanticTokenProvider<Tag> {
    override fun getMapNameTokenInfo(map: Tag) = StringRangeTree.TokenInfo(TokenType.PROPERTY, 0)

    override fun getNodeTokenInfo(node: Tag) = when(node) {
        is StringTag -> StringRangeTree.TokenInfo(TokenType.STRING, 0)
        is NumericTag -> {
            val startChar = input[tree.ranges[node]!!.start]
            if(startChar == 't' || startChar == 'f')
                // Number is a boolean
                StringRangeTree.TokenInfo(TokenType.ENUM_MEMBER, 0)
            else
                StringRangeTree.TokenInfo(TokenType.NUMBER, 0)
        }
        else -> null
    }

    override fun getAdditionalTokens(node: Tag) = when(node) {
        is CollectionTag -> {
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