package net.papierkorb2292.command_crafter.editor.processing.symbols

import java.util.stream.Stream

interface ImmutableSymbolAccess {
    fun <TKey> getKeys(type: SymbolType<TKey>): Stream<TKey>
    fun <TKey> getSymbolOrNull(type: SymbolType<TKey>, key: TKey): Symbol?

    object Dummy : ImmutableSymbolAccess {
        override fun <TKey> getKeys(type: SymbolType<TKey>): Stream<TKey> = Stream.empty()
        override fun <TKey> getSymbolOrNull(type: SymbolType<TKey>, key: TKey): Symbol? = null
    }
}