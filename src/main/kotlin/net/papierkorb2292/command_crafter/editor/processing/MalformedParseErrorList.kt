package net.papierkorb2292.command_crafter.editor.processing

import net.minecraft.util.parsing.packrat.ErrorCollector
import net.minecraft.util.parsing.packrat.ErrorEntry
import net.minecraft.util.parsing.packrat.SuggestionSupplier

class MalformedParseErrorList<S: Any> : ErrorCollector<S> {
    private val malformedLists = mutableListOf<Pair<Int, ErrorCollector.LongestOnly<S>>>()
    private var backingList = ErrorCollector.LongestOnly<S>()
    var lastMalformedEndCursor = -1

    fun startMalformedScope(): (endCursor: Int, termMatched: Boolean) -> Unit {
        val prevList = backingList
        backingList = ErrorCollector.LongestOnly<S>()
        return { endCursor: Int, termMatched: Boolean ->
            malformedLists.add(Pair(endCursor, backingList))
            backingList = prevList
            if(!termMatched)
                lastMalformedEndCursor = endCursor
        }
    }

    fun swapLastMalformedEndCursor(new: Int): Int {
        val prev = lastMalformedEndCursor
        lastMalformedEndCursor = new
        return prev
    }

    override fun store(
        cursor: Int,
        suggestions: SuggestionSupplier<S>,
        reason: Any,
    ) {
        if(cursor == lastMalformedEndCursor) {
            // Don't add errors when the cursor is still at the position where a malformed term ended,
            // because no later terms should be suggested
            return
        }
        lastMalformedEndCursor = -1 // Since the cursor moved, the parser either back-tracked or progressed past the malformed part, so suggestions are fine again
        backingList.store(cursor, suggestions, reason)
    }

    fun getErrors(): List<ErrorEntry<S>> = malformedLists.flatMap { it.second.entries() } + backingList.entries()

    override fun finish(cursor: Int) {
        backingList.finish(cursor)
        malformedLists.removeAll { it.first < cursor }
    }
}