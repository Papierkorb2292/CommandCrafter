package net.papierkorb2292.command_crafter.editor.processing.symbols

class Symbol {
    val declarations = SymbolLocationMap<SymbolDocInfo?>()
    val usages = SymbolLocationMap<Nothing?>()

    fun isEmpty() = declarations.isEmpty() && usages.isEmpty()
}
