package net.papierkorb2292.command_crafter.editor.processing.symbols

interface SymbolKind<TKindInfo> {
    object Declaration : SymbolKind<SymbolDocInfo?> {
        override fun toString() = "Declaration"
    }
    object Reference : SymbolKind<Nothing?> {
        override fun toString() = "Reference"
    }
}