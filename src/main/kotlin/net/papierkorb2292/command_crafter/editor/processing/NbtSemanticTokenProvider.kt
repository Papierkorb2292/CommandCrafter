package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import net.minecraft.nbt.AbstractNbtList
import net.minecraft.nbt.AbstractNbtNumber
import net.minecraft.nbt.NbtByte
import net.minecraft.nbt.NbtByteArray
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtIntArray
import net.minecraft.nbt.NbtLongArray
import net.minecraft.nbt.NbtString
import net.minecraft.nbt.scanner.NbtScanner
import net.minecraft.nbt.visitor.NbtElementVisitor
import java.io.DataOutput

class NbtSemanticTokenProvider(val tree: StringRangeTree<NbtElement>) : StringRangeTree.SemanticTokenProvider<NbtElement> {
    // Only used in a StringRangeTree to keep nodes separate instances
    // and to keep more information about the original form in the input string
    class NbtBoolean(boolean: Boolean) : NbtByte(if(boolean) 1 else 0)
    class NbtUnknown : NbtElement {
        private fun throwUnsupported(): Nothing =
            throw UnsupportedOperationException("Can't process NbtUnknown element")

        override fun copy() = NbtUnknown()

        override fun write(output: DataOutput) = throwUnsupported()
        override fun getType() = throwUnsupported()
        override fun getNbtType() = throwUnsupported()
        override fun getSizeInBytes() = throwUnsupported()
        override fun accept(visitor: NbtElementVisitor) = throwUnsupported()
        override fun doAccept(visitor: NbtScanner) = throwUnsupported()
    }

    override fun getMapNameTokenInfo(map: NbtElement) = StringRangeTree.TokenInfo(TokenType.PROPERTY, 0)

    override fun getNodeTokenInfo(node: NbtElement) = when(node) {
        is NbtBoolean -> StringRangeTree.TokenInfo(TokenType.ENUM_MEMBER, 0)
        is NbtString -> StringRangeTree.TokenInfo(TokenType.STRING, 0)
        is AbstractNbtNumber -> StringRangeTree.TokenInfo(TokenType.NUMBER, 0)
        else -> null
    }

    override fun getAdditionalTokens(node: NbtElement) = when(node) {
        is NbtByteArray, is NbtIntArray, is NbtLongArray -> {
            val nodeStart = tree.ranges[node]!!.start
            listOf(
                StringRangeTree.AdditionalToken(
                    StringRange(nodeStart + 1, nodeStart + 2),
                    StringRangeTree.TokenInfo(TokenType.TYPE, 0)
                )
            )
        }
        else -> emptyList()
    }
}