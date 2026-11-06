package net.papierkorb2292.command_crafter.editor.processing.symbols

interface SymbolLocationVisitable {
    fun visit(visitor: SymbolLocationVisitor)

    interface SymbolLocationVisitor {
        fun <TKey, TKindInfo> acceptSymbolLocation(type: SymbolType<TKey>, symbolKey: TKey, kind: SymbolKind<TKindInfo>, location: SymbolLocationMap.SymbolLocation<TKindInfo>)
    }
}