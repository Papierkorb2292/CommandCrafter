package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.util.parsing.packrat.DelayedException
import net.minecraft.util.parsing.packrat.ParseState
import net.minecraft.util.parsing.packrat.Rule
import net.minecraft.util.parsing.packrat.commands.CommandParserState

/**
 * Reads unquoted strings like [net.minecraft.util.parsing.packrat.commands.UnquotedStringParseRule],
 * but also allows : and # in the string (for ids)
 */
class LenientUnquotedStringParseRule(val minSize: Int, val error: DelayedException<CommandSyntaxException>) : Rule<CommandParserState, String> {
    override fun parse(parseState: ParseState<CommandParserState>): String? {
        val reader = parseState.input().reader()
        reader.skipWhitespace()
        val startCursor = parseState.mark()
        while(reader.canRead() && isAllowed(reader.peek())) {
            reader.skip()
        }
        val string = reader.string.substring(startCursor, reader.cursor)
        if(string.length < this.minSize) {
            parseState.errorCollector().store(startCursor, this.error)
            return null
        } else {
            return string
        }
    }

    fun isAllowed(c: Char): Boolean {
        return c in '0'..'9'
                || c in 'A'..'Z'
                || c in 'a'..'z'
                || c == '_'
                || c == '-'
                || c == '.'
                || c == '+'
                || c == ':'
                || c == '#'
    }
}