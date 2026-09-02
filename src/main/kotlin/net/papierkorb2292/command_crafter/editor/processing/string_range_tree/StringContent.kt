package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper

data class StringContent(val content: String, val cursorMapper: SplitProcessedInputCursorMapper, val escaper: StringEscaper) {
    fun interface StringContentGetter<TNode> {
        fun getStringContent(node: TNode): StringContent?
    }

    companion object {
        val PACKET_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            StringContent::content,
            SplitProcessedInputCursorMapper.PACKET_CODEC,
            StringContent::cursorMapper,
            StringEscaper.PACKET_CODEC,
            StringContent::escaper,
            ::StringContent
        )
    }
}