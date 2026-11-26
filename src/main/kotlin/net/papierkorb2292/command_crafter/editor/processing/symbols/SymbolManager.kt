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
    override fun <TKey> getKeys(type: SymbolType<TKey>, filter: SymbolDeclarationFilter?): Stream<TKey> =
        getRegistry(type).getKeys(filter).stream()

    @Synchronized
    override fun <TKey> getSymbolOrNull(type: SymbolType<TKey>, key: TKey, filter: SymbolDeclarationFilter?): Symbol? =
        getRegistry(type).getSymbolOrNull(key, filter)

    @Synchronized
    fun bind(uri: String, visitable: SymbolLocationVisitable) {
        visitable.visitSymbols(object : SymbolLocationVisitable.SymbolLocationVisitor {
            override fun <TKey, TKindInfo> acceptSymbolLocation(
                type: SymbolType<TKey>,
                symbolKey: TKey,
                kind: SymbolKind<TKindInfo>,
                location: SymbolLocationMap.SymbolLocation<TKindInfo>,
            ) {
                getRegistry(type)
                    .getOrCreateSymbol(symbolKey)
                    .getSymbolLocations(kind)
                    .workspaceLocations
                    .getOrPut(uri) { mutableListOf() }
                    .add(location)
            }
        })
    }

    @Synchronized
    fun unbind(uri: String, visitable: SymbolLocationVisitable) {
        visitable.visitSymbols(object : SymbolLocationVisitable.SymbolLocationVisitor {
            override fun <TKey, TKindInfo> acceptSymbolLocation(
                type: SymbolType<TKey>,
                symbolKey: TKey,
                kind: SymbolKind<TKindInfo>,
                location: SymbolLocationMap.SymbolLocation<TKindInfo>,
            ) {
                val registry = getRegistry(type)
                val symbol = registry.getSymbolOrNull(symbolKey) ?: return
                symbol.getSymbolLocations(kind)
                    .workspaceLocations
                    .remove(uri)
                registry.tryRemoveEmptySymbol(symbolKey)
            }
        })
    }
}