package net.papierkorb2292.command_crafter.editor.processing.symbols

interface SymbolKind<TKindInfo> {
    object Declaration : SymbolKind<SymbolDocInfo?>
    object Reference : SymbolKind<Nothing?>
}