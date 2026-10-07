package net.papierkorb2292.command_crafter.editor.processing.symbols

import java.util.stream.Stream

/**
 * Analyzers only need to access a stream of available symbols (for completions).
 * All other symbol functionality is done separately after the analyzer has finished.
 */
interface AnalyzerSymbolAccess {
    fun <TKey> getKeys(type: SymbolType<TKey>): Stream<TKey>

    object Dummy : AnalyzerSymbolAccess {
        override fun <TKey> getKeys(type: SymbolType<TKey>): Stream<TKey> = Stream.empty()
    }
}