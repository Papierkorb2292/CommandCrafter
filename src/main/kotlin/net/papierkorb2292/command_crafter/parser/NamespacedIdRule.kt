package net.papierkorb2292.command_crafter.parser

import net.minecraft.IdentifierException
import net.minecraft.resources.Identifier
import net.minecraft.util.parsing.packrat.ParseState
import net.minecraft.util.parsing.packrat.Rule
import net.minecraft.util.parsing.packrat.commands.CommandParserState

class NamespacedIdRule<TState : CommandParserState> : Rule<TState, Identifier> {
    override fun parse(state: ParseState<TState>): Identifier? {
        val reader = state.input().reader()
        reader.skipWhitespace()
        val start = reader.cursor

        return try {
            while(reader.canRead() && Identifier.isAllowedInIdentifier(reader.peek()))
                reader.skip()

            val id = reader.string.substring(start, reader.cursor)
            if(':' !in id)
                return null
            Identifier.parse(id)
        } catch(_: IdentifierException) {
            state.restore(start)
            null
        }
    }
}