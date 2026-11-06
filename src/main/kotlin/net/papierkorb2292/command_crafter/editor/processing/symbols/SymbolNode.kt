package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.ActualSyntaxNode
import org.eclipse.lsp4j.Hover
import java.util.concurrent.CompletableFuture

data class SymbolNode<TKey, TKindInfo>(
    private val type: SymbolType<TKey>,
    private val symbolKey: TKey,
    private val kind: SymbolKind<TKindInfo>,
    private val locationInfo: SymbolLocationMap.SymbolLocation<TKindInfo>,
    private val symbolAccess: ImmutableSymbolAccess,
    private val languageServer: MinecraftLanguageServer?,
) : ActualSyntaxNode, SymbolLocationVisitable {
    constructor(
        type: SymbolType<TKey>,
        symbolKey: TKey,
        kind: SymbolKind<TKindInfo>,
        locationInfo: SymbolLocationMap.SymbolLocation<TKindInfo>,
        analyzingResourceCreator: AnalyzingResourceCreator,
    ) : this(
        type,
        symbolKey,
        kind,
        locationInfo,
        analyzingResourceCreator.symbols,
        analyzingResourceCreator.languageServer
    )




    override fun getDefinition(cursor: Int): CompletableFuture<ActualSyntaxNode.Definition>? = symbolAccess.getSymbolOrNull(type, symbolKey)?.getDefinition(locationInfo.range)
    override fun getHover(cursor: Int): CompletableFuture<Hover>? = symbolAccess.getSymbolOrNull(type, symbolKey)?.getHover(languageServer ?: return null)

    override fun visit(visitor: SymbolLocationVisitable.SymbolLocationVisitor) {
        visitor.acceptSymbolLocation(type, symbolKey, kind, locationInfo)
    }
}