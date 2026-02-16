package net.papierkorb2292.command_crafter.editor.processing.helper

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

/**
 * Collection of callbacks for a region of code. A position in the code can be
 * associated with at most one of these callbacks, so [ActualSyntaxNode] is for
 * those callbacks that cannot be combined with others (like hovers). Those callbacks should only
 * be added after this region of the code has been successfully parsed and determined
 * to be off the correct type for the callback.
 *
 * Returned LSP4J objects may be modified by the caller, so they should not be cached
 * (but empty lists are allowed to be cached)
 */
interface ActualSyntaxNode {
    fun getDefinition(cursor: Int): CompletableFuture<Either<List<Location>, List<LocationLink>>>?
    fun getHover(cursor: Int): CompletableFuture<Hover>?
}

fun ActualSyntaxNode.offsetActualInput(offset: Int) = object : ActualSyntaxNode {
    override fun getDefinition(cursor: Int) = this@offsetActualInput.getDefinition(cursor + offset)
    override fun getHover(cursor: Int) = this@offsetActualInput.getHover(cursor + offset)
}

fun ActualSyntaxNode.offsetActualOutput(offset: Position) = object : ActualSyntaxNode {
    override fun getDefinition(cursor: Int) = this@offsetActualOutput.getDefinition(cursor)?.thenApply { definition ->
        if(definition.isRight)
            Either.forRight(definition.right.map { link ->
                if(link.originSelectionRange != null)
                    link.originSelectionRange = offset.offsetRange(link.originSelectionRange)
                link
            })
        else definition
    }

    override fun getHover(cursor: Int) = this@offsetActualOutput.getHover(cursor)?.thenApply { hover ->
        if(hover.range != null)
            hover.range = offset.offsetRange(hover.range)
        hover
    }
}