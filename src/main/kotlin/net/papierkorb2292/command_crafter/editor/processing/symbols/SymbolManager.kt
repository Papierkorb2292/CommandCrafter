package net.papierkorb2292.command_crafter.editor.processing.symbols

import java.util.stream.Stream

class SymbolManager : AnalyzerSymbolAccess {
    private val symbolRegistries: MutableMap<SymbolType<*>, SymbolRegistry<*>> = mutableMapOf()

    fun <TKey> getRegistry(symbolType: SymbolType<TKey>): SymbolRegistry<TKey> {
        @Suppress("UNCHECKED_CAST")
        return symbolRegistries.getOrPut(symbolType) { SymbolRegistry<TKey>() } as SymbolRegistry<TKey>
    }

    override fun <TKey> getKeys(type: SymbolType<TKey>): Stream<TKey> =
        getRegistry(type).getKeys().stream()
}