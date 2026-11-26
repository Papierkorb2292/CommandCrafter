package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.ActualSyntaxNode
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class OpenFileSymbolAccess(
    private val uri: String,
    private val environmentSymbols: ImmutableSymbolAccess,
    val fileSymbols: SymbolManager = SymbolManager()
) : ImmutableSymbolAccess {

    override fun <TKey> getKeys(type: SymbolType<TKey>, filter: SymbolDeclarationFilter?): Stream<TKey> = Stream.concat(
        fileSymbols.getKeys(type, filter), // We can assume that fileSymbols only contains symbols from the current file, so we don't need to filter them by file path
        environmentSymbols.getKeys(type, OutsideFileDeclarationFilter(uri, filter))
    )

    override fun <TKey> getSymbolOrNull(type: SymbolType<TKey>, key: TKey, filter: SymbolDeclarationFilter?): ImmutableSymbol? {
        val fileSymbol = fileSymbols.getSymbolOrNull(type, key, filter)
        val environmentSymbol = environmentSymbols.getSymbolOrNull(type, key, OutsideFileDeclarationFilter(uri, filter))
        if(environmentSymbol == null) return fileSymbol
        if(fileSymbol == null) return environmentSymbol

        // Combine both symbols
        return object : ImmutableSymbol {
            override fun getDefinition(originSelectionRange: Range?): CompletableFuture<ActualSyntaxNode.Definition> =
                fileSymbol.getDefinition(originSelectionRange).thenCombine(environmentSymbol.getDefinition(originSelectionRange)) { fileDef, envDef ->
                    // Combine the two definitions into one
                    if(!fileDef.isTargetAbsolute || !envDef.isTargetAbsolute)
                        throw IllegalStateException("Symbol definition was expected to be absolute")
                    val combinedLocations = mutableListOf<LocationLink>()
                    combinedLocations.addAll(fileDef.location.right ?: throw IllegalStateException("Symbol definition was expected to consist of LocationLinks"))
                    combinedLocations.addAll(envDef.location.right ?: throw IllegalStateException("Symbol definition was expected to consist of LocationLinks"))
                    ActualSyntaxNode.Definition(Either.forRight(combinedLocations), true)
                }

            override fun getHover(languageServer: MinecraftLanguageServer): CompletableFuture<Hover>? =
                fileSymbol.getHover(languageServer) ?: environmentSymbol.getHover(languageServer)
        }
    }

    class OutsideFileDeclarationFilter(
        private val filePath: String,
        private val delegate: SymbolDeclarationFilter?,
    ) : SymbolDeclarationFilter {
        override fun canUse(uri: String, location: SymbolLocationMap.SymbolLocation<SymbolDocInfo?>): Boolean {
            return filePath != uri && (delegate == null || delegate.canUse(uri, location))
        }
    }
}