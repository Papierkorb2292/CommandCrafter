package net.papierkorb2292.command_crafter.editor.processing

import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.papierkorb2292.command_crafter.editor.processing.helper.createCursorMapperForEscapedCharacters

class NbtStringContentGetter(val tree: StringRangeTree<Tag>, val input: String): StringRangeTree.StringContentGetter<Tag> {
    override fun getStringContent(node: Tag): StringRangeTree.StringContent? {
        if(node !is StringTag)
            return null
        val range = tree.ranges[node]!!
        val firstChar = input[range.start]
        val isQuoted = firstChar == '"' || firstChar == '\''
        val sourceString =
            if(isQuoted) {
                // If the string is missing content and end quotes, end-1 will be before start+1
                if(range.end - 1 > range.start)
                    input.substring(range.start + 1, range.end - 1)
                else
                    input.substring(range.start + 1, range.end)
            } else
                input.substring(range.start, range.end)
        return StringRangeTree.StringContent(
            node.value,
            createCursorMapperForEscapedCharacters(sourceString, range.start + 1),
            if(isQuoted) StringRangeTree.StringEscaper.escapeForQuotes(firstChar.toString()) else StringRangeTree.StringEscaper.Identity
        )
    }
}