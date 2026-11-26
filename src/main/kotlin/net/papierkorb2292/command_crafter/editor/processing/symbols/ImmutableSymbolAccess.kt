package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import org.eclipse.lsp4j.Diagnostic
import java.util.stream.Stream

interface ImmutableSymbolAccess {
    fun <TKey> getKeys(type: SymbolType<TKey>, filter: SymbolDeclarationFilter? = null): Stream<TKey>
    fun <TKey> getSymbolOrNull(type: SymbolType<TKey>, key: TKey, filter: SymbolDeclarationFilter? = null): ImmutableSymbol?

    object Dummy : ImmutableSymbolAccess {
        override fun <TKey> getKeys(type: SymbolType<TKey>, filter: SymbolDeclarationFilter?): Stream<TKey> = Stream.empty()
        override fun <TKey> getSymbolOrNull(type: SymbolType<TKey>, key: TKey, filter: SymbolDeclarationFilter?): ImmutableSymbol? = null
    }
}

fun ImmutableSymbolAccess.markUnknownSymbols(
    analyzingResult: AnalyzingResult,
): List<Diagnostic> {
    val diagnostics = mutableListOf<Diagnostic>()
    for(symbolNode in analyzingResult.symbolNodes) {
        if(symbolNode.kind != SymbolKind.Reference)
            continue
        if(symbolNode.getSymbol(this) != null) {
            // Symbol exists
            continue
        }
        diagnostics += Diagnostic().apply {
            range = symbolNode.locationInfo.range
            val errorInfo = symbolNode.errorInfo!!
            message = errorInfo.message.string
            severity = errorInfo.severity
        }
    }
    return diagnostics
}