package net.papierkorb2292.command_crafter.editor.processing.symbols

interface SymbolDeclarationFilter {
    fun canUse(uri: String, location: SymbolLocationMap.SymbolLocation<SymbolDocInfo?>): Boolean
}