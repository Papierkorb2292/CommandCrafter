package net.papierkorb2292.command_crafter.editor.processing.symbols

import java.util.stream.Stream

class SymbolManager : ImmutableSymbolAccess {
    private val symbolRegistries: MutableMap<SymbolType<*>, SymbolRegistry<*>> = mutableMapOf()

    private fun <TKey> getRegistry(symbolType: SymbolType<TKey>): SymbolRegistry<TKey> {
        @Suppress("UNCHECKED_CAST")
        return symbolRegistries.getOrPut(symbolType) { SymbolRegistry<TKey>() } as SymbolRegistry<TKey>
    }

    @Synchronized
    fun <TKey> modifyRegistry(symbolType: SymbolType<TKey>, modifier: (SymbolRegistry<TKey>) -> Unit) {
        modifier(getRegistry(symbolType))
    }

    @Synchronized
    override fun <TKey> getKeys(type: SymbolType<TKey>): Stream<TKey> =
        getRegistry(type).getKeys().stream()

    @Synchronized
    override fun <TKey> getSymbolOrNull(type: SymbolType<TKey>, key: TKey): Symbol? =
        getRegistry(type).getSymbolOrNull(key)
}