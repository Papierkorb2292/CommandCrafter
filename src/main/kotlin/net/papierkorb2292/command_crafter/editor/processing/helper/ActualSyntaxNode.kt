package net.papierkorb2292.command_crafter.editor.processing.helper

import org.eclipse.lsp4j.*
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
    fun getDefinition(cursor: Int): CompletableFuture<Definition>?
    fun getHover(cursor: Int): CompletableFuture<Hover>?

    data class Definition(val location: Either<List<Location>, List<LocationLink>>, val isTargetAbsolute: Boolean) {
        fun mapSource(mapper: (Range) -> Range) {
            if(location.isLeft) return // The Location class doesn't contain source ranges
            location.right.forEach { link ->
                if(link.originSelectionRange != null)
                    link.originSelectionRange = mapper(link.originSelectionRange)
            }
        }

        fun mapTarget(mapper: (Range) -> Range) {
            if(isTargetAbsolute) return
            location.map(
                { locations -> locations.forEach { location ->
                      location.range = mapper(location.range)
                } },
                { links -> links.forEach { link ->
                    link.targetRange = mapper(link.targetRange)
                } },
            )
        }
    }
}

fun ActualSyntaxNode.offsetActualInput(offset: Int) = object : ActualSyntaxNode {
    override fun getDefinition(cursor: Int) = this@offsetActualInput.getDefinition(cursor + offset)
    override fun getHover(cursor: Int) = this@offsetActualInput.getHover(cursor + offset)
}

fun ActualSyntaxNode.offsetActualOutput(offset: Position) = object : ActualSyntaxNode {
    override fun getDefinition(cursor: Int) = this@offsetActualOutput.getDefinition(cursor)?.thenApply { definition ->
        definition.mapSource { offset.offsetRange(it) }
        definition.mapTarget { offset.offsetRange(it) }
        definition
    }

    override fun getHover(cursor: Int) = this@offsetActualOutput.getHover(cursor)?.thenApply { hover ->
        if(hover.range != null)
            hover.range = offset.offsetRange(hover.range)
        hover
    }
}

fun ActualSyntaxNode.offsetActualOutputDifference(offset: Position) = object : ActualSyntaxNode {
    override fun getDefinition(cursor: Int) = this@offsetActualOutputDifference.getDefinition(cursor)?.thenApply { definition ->
        definition.mapSource { offset.differenceTo(it) }
        definition.mapTarget { offset.differenceTo(it) }
        definition
    }

    override fun getHover(cursor: Int) = this@offsetActualOutputDifference.getHover(cursor)?.thenApply { hover ->
        if(hover.range != null)
            hover.range = offset.differenceTo(hover.range)
        hover
    }
}