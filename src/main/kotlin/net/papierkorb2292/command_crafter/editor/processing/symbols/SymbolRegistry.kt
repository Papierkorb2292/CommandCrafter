package net.papierkorb2292.command_crafter.editor.processing.symbols

class SymbolRegistry<TKey> {
    private val symbols: MutableMap<TKey, Symbol> = mutableMapOf()

    fun getOrCreateSymbol(key: TKey): Symbol =
        symbols.getOrPut(key) { Symbol() }
    fun getSymbolOrNull(key: TKey, filter: SymbolDeclarationFilter? = null): Symbol? {
        val symbol = symbols[key] ?:  return null
        return if(filter != null && symbol.matchesDeclarationFilter(filter)) symbol else null
    }

    fun tryRemoveEmptySymbol(key: TKey): Boolean {
        val symbol = symbols[key] ?: return false
        if(!symbol.isEmpty())
            return false
        symbols.remove(key)
        return true
    }

    fun getKeys(filter: SymbolDeclarationFilter? = null): Collection<TKey> {
        if(filter == null)
            return symbols.keys
        return symbols.mapNotNull { (key, symbol) ->
            if(symbol.matchesDeclarationFilter(filter)) key else null
        }
    }
}