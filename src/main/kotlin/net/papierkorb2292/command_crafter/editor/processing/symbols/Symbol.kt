package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.ActualSyntaxNode
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

class Symbol : ImmutableSymbol {
    private val symbolLocations: MutableMap<SymbolKind<*>?, SymbolLocationMap<*>> = mutableMapOf()
    val declarations = getSymbolLocations(SymbolKind.Declaration)
    val references = getSymbolLocations(SymbolKind.Reference)

    @Suppress("UNCHECKED_CAST")
    fun <TKindInfo> getSymbolLocations(kind: SymbolKind<TKindInfo>): SymbolLocationMap<TKindInfo> =
        symbolLocations.getOrPut(kind) { SymbolLocationMap<TKindInfo>() } as SymbolLocationMap<TKindInfo>

    fun isEmpty() = symbolLocations.values.all { it.isEmpty() }

    fun matchesDeclarationFilter(filter: SymbolDeclarationFilter): Boolean {
        return declarations.workspaceLocations.any { (uri, locations) ->
            locations.any { location -> filter.canUse(uri, location) }
        }
    }

    override fun getDefinition(originSelectionRange: Range?): CompletableFuture<ActualSyntaxNode.Definition> {
        val links = declarations.workspaceLocations.flatMap { (uri, workspaceLocations) ->
            workspaceLocations.map { workspaceLocation ->
                LocationLink(
                    uri,
                    workspaceLocation.range,
                    workspaceLocation.selectionRangeOverride ?: workspaceLocation.range,
                    originSelectionRange
                )
        } }
        return CompletableFuture.completedFuture(ActualSyntaxNode.Definition(Either.forRight(links), true))
    }

    override fun getHover(languageServer: MinecraftLanguageServer): CompletableFuture<Hover>? =
        declarations.workspaceLocations.firstNotNullOfOrNull { (_, workspaceLocations) ->
            workspaceLocations.firstNotNullOfOrNull { workspaceLocation ->
                val documentation = workspaceLocation.extraInfo?.documentation ?: return@firstNotNullOfOrNull null
                languageServer.hoverDocumentation(documentation, workspaceLocation.range)
            }
        }
}
