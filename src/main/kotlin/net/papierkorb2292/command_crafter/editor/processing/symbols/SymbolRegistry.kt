package net.papierkorb2292.command_crafter.editor.processing.symbols

class SymbolRegistry<TKey> {
    private val symbols: MutableMap<TKey, Symbol> = mutableMapOf()

    fun getOrCreateSymbol(key: TKey): Symbol =
        symbols.getOrPut(key) { Symbol() }
    fun getSymbolOrNull(key: TKey): Symbol? =
        symbols[key]

    fun tryRemoveEmptySymbol(key: TKey): Boolean {
        val symbol = symbols[key] ?: return false
        if(!symbol.isEmpty())
            return false
        symbols.remove(key)
        return true
    }

    fun getKeys(): Set<TKey> = symbols.keys
}