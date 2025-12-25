package net.papierkorb2292.command_crafter.editor.processing

import net.minecraft.util.parsing.packrat.ErrorEntry
import net.minecraft.util.parsing.packrat.ErrorCollector
import net.minecraft.util.parsing.packrat.SuggestionSupplier

class MalformedParseErrorList<S: Any> : ErrorCollector<S> {
    private val malformedLists = mutableListOf<Pair<Int, ErrorCollector.LongestOnly<S>>>()
    private var backingList = ErrorCollector.LongestOnly<S>()

    fun startMalformedScope(): (endCursor: Int) -> Unit {
        val prevList = backingList
        backingList = ErrorCollector.LongestOnly<S>()
        return { endCursor: Int ->
            malformedLists.add(Pair(endCursor, backingList))
            backingList = prevList
        }
    }

    override fun store(
        cursor: Int,
        suggestions: SuggestionSupplier<S>,
        reason: Any,
    ) {
        backingList.store(cursor, suggestions, reason)
    }

    fun getErrors(): List<ErrorEntry<S>> = malformedLists.flatMap { it.second.entries() } + backingList.entries()

    override fun finish(cursor: Int) {
        backingList.finish(cursor)
        malformedLists.removeAll { it.first < cursor }
    }
}