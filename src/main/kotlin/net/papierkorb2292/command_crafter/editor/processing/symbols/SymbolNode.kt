package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.ActualSyntaxNode
import net.papierkorb2292.command_crafter.editor.processing.helper.offsetBy
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.Position
import java.util.concurrent.CompletableFuture

data class SymbolNode<TKey, TKindInfo>(
    val type: SymbolType<TKey>,
    val symbolKey: TKey,
    val kind: SymbolKind<TKindInfo>,
    val locationInfo: SymbolLocationMap.SymbolLocation<TKindInfo>,
) : SymbolLocationVisitable {

    inline fun mapSymbolLocation(mapper: (Position) -> Position): SymbolNode<TKey, TKindInfo> {
        val newLocationInfo = locationInfo.mapPositions(mapper)
        if(newLocationInfo == locationInfo)
            return this
        return copy(locationInfo = newLocationInfo)
    }

    fun offsetSymbolLocationOutput(offset: Position): SymbolNode<TKey, TKindInfo> =
        mapSymbolLocation { offset.offsetBy(it) }

    override fun visitSymbols(visitor: SymbolLocationVisitable.SymbolLocationVisitor) {
        visitor.acceptSymbolLocation(type, symbolKey, kind, locationInfo)
    }

    fun getActualSyntaxNode(analyzingResourceCreator: AnalyzingResourceCreator): ActualSyntaxNode = object : ActualSyntaxNode {
        override fun getDefinition(cursor: Int) =
            analyzingResourceCreator.symbols.getSymbolOrNull(type, symbolKey)?.getDefinition(locationInfo.range)

        override fun getHover(cursor: Int): CompletableFuture<Hover>? {
            val languageServer = analyzingResourceCreator.languageServer ?: return null
            return analyzingResourceCreator.symbols.getSymbolOrNull(type, symbolKey)?.getHover(languageServer)
        }
    }
}