package net.papierkorb2292.command_crafter.editor.processing

import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtString
import net.papierkorb2292.command_crafter.editor.processing.helper.createCursorMapperForEscapedCharacters
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper

class NbtStringContentGetter(val tree: StringRangeTree<NbtElement>, val input: String): (NbtElement) -> Pair<String, SplitProcessedInputCursorMapper>? {
    override fun invoke(p1: NbtElement): Pair<String, SplitProcessedInputCursorMapper>? {
        if(p1 !is NbtString)
            return null
        val range = tree.ranges[p1]!!
        val firstChar = input[range.start]
        val sourceString =
            if(firstChar == '"' || firstChar == '\'') {
                // If the string is missing content and end quotes, end-1 will be before start+1
                if(range.end - 1 > range.start)
                    input.substring(range.start + 1, range.end - 1)
                else
                    input.substring(range.start + 1, range.end)
            } else
                input.substring(range.start, range.end)
        return Pair(p1.asString(), createCursorMapperForEscapedCharacters(sourceString, range.start + 1))
    }
}