package net.papierkorb2292.command_crafter.editor.processing.symbols

import com.mojang.brigadier.Message
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.ActualSyntaxNode
import net.papierkorb2292.command_crafter.editor.processing.helper.offsetBy
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.Position
import java.util.concurrent.CompletableFuture

data class SymbolNode<TKey, TKindInfo>(
    val type: SymbolType<TKey>,
    val symbolKey: TKey,
    val kind: SymbolKind<TKindInfo>,
    val locationInfo: SymbolLocationMap.SymbolLocation<TKindInfo>,
    val errorInfo: ErrorInfo?, // TODO: Specify everywhere and remove vanilla errors
) : SymbolLocationVisitable {

    init {
        val isReference = kind == SymbolKind.Reference
        val hasErrorInfo = errorInfo != null
        require(isReference == hasErrorInfo) {
            "A SymbolNode must have ErrorInfo if and only if it is a reference"
        }
    }

    inline fun mapSymbolLocation(mapper: (Position) -> Position): SymbolNode<TKey, TKindInfo> {
        val newLocationInfo = locationInfo.mapPositions(mapper)
        if(newLocationInfo == locationInfo)
            return this
        return copy(locationInfo = newLocationInfo)
    }

    fun offsetSymbolLocationOutput(offset: Position): SymbolNode<TKey, TKindInfo> =
        mapSymbolLocation { offset.offsetBy(it) }

    fun getSymbol(symbolAccess: ImmutableSymbolAccess): ImmutableSymbol? =
        symbolAccess.getSymbolOrNull(type, symbolKey)

    override fun visitSymbols(visitor: SymbolLocationVisitable.SymbolLocationVisitor) {
        visitor.acceptSymbolLocation(type, symbolKey, kind, locationInfo)
    }

    fun getActualSyntaxNode(analyzingResourceCreator: AnalyzingResourceCreator): ActualSyntaxNode = object : ActualSyntaxNode {
        override fun getDefinition(cursor: Int) =
            getSymbol(analyzingResourceCreator.symbols)?.getDefinition(locationInfo.range)

        override fun getHover(cursor: Int): CompletableFuture<Hover>? {
            val languageServer = analyzingResourceCreator.languageServer ?: return null
            return getSymbol(analyzingResourceCreator.symbols)?.getHover(languageServer)
        }
    }

    data class ErrorInfo(val severity: DiagnosticSeverity, val message: Message)
}