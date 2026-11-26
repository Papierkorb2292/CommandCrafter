package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.ActualSyntaxNode
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.Range
import java.util.concurrent.CompletableFuture

interface ImmutableSymbol {
    /**
     * Gets the definition of this symbol. The returned definition must consist of [org.eclipse.lsp4j.LocationLink]s and have [ActualSyntaxNode.Definition.isTargetAbsolute] be true,
     */
    fun getDefinition(originSelectionRange: Range? = null): CompletableFuture<ActualSyntaxNode.Definition>
    fun getHover(languageServer: MinecraftLanguageServer): CompletableFuture<Hover>?
}