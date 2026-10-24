package net.papierkorb2292.command_crafter.editor.processing.symbols

/**
 * Defines whether a symbol type should be analyzed and if the analyzer can provide documentation
 */
enum class SymbolAnalyzeMode {
    NoAnalyzer,
    Analyzer,
    AnalyzerWithDocumentation
}