package net.papierkorb2292.command_crafter.editor.processing

import net.minecraft.util.packrat.ParseError
import net.minecraft.util.packrat.ParseErrorList
import net.minecraft.util.packrat.Suggestable

class MalformedParseErrorList<S> : ParseErrorList<S> {
    private val malformedLists = mutableListOf<Pair<Int, ParseErrorList.Impl<S>>>()
    private var backingList = ParseErrorList.Impl<S>()

    fun startMalformedScope(): (endCursor: Int) -> Unit {
        val prevList = backingList
        backingList = ParseErrorList.Impl<S>()
        return { endCursor: Int ->
            malformedLists.add(Pair(endCursor, backingList))
            backingList = prevList
        }
    }

    override fun add(
        cursor: Int,
        suggestions: Suggestable<S>,
        reason: Any,
    ) {
        backingList.add(cursor, suggestions, reason)
    }

    fun getErrors(): List<ParseError<S>> = malformedLists.flatMap { it.second.getErrors() } + backingList.errors

    override fun setCursor(cursor: Int) {
        backingList.cursor = cursor
        malformedLists.removeAll { it.first < cursor }
    }
}