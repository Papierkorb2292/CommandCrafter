package net.papierkorb2292.command_crafter.editor.processing.symbols

interface SymbolLocationVisitable {
    fun visit(visitor: SymbolLocationVisitor)

    interface SymbolLocationVisitor {
        fun <TKindInfo> acceptSymbolLocation(kind: SymbolKind<TKindInfo>, location: SymbolLocationMap.SymbolLocation<TKindInfo>)
    }
}