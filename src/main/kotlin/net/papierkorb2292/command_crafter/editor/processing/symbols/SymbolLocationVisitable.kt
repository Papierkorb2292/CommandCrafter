package net.papierkorb2292.command_crafter.editor.processing.symbols

interface SymbolLocationVisitable {
    fun visitSymbols(visitor: SymbolLocationVisitor)

    // TODO: Implement visitors that add/remove from the symbol table respectively
    interface SymbolLocationVisitor {
        fun <TKey, TKindInfo> acceptSymbolLocation(type: SymbolType<TKey>, symbolKey: TKey, kind: SymbolKind<TKindInfo>, location: SymbolLocationMap.SymbolLocation<TKindInfo>)
    }
}