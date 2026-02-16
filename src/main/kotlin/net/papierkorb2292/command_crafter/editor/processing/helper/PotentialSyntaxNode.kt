package net.papierkorb2292.command_crafter.editor.processing.helper

import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

/**
 * Collection of callbacks for a region of code. A position in the code can be
 * associated with multiple of these callbacks, so [PotentialSyntaxNode] is for those
 * callbacks that can be combined with others (like completions). These callbacks
 * can be added whenever a region of the code could allow the type for this callback
 * to be read, even if the actual code is different.
 *
 * Returned LSP4J objects may be modified by the caller, so they should not be cached
 * (but empty lists are allowed to be cached)
 */
interface PotentialSyntaxNode {
    fun getCompletions(cursor: Int, context: CompletionContext): CompletableFuture<List<CompletionItem>>?
}

fun PotentialSyntaxNode.offsetPotentialInput(offset: Int) = object : PotentialSyntaxNode {
    override fun getCompletions(cursor: Int, context: CompletionContext) = this@offsetPotentialInput.getCompletions(cursor + offset, context)
}

fun PotentialSyntaxNode.offsetPotentialOutput(offset: Position) = object : PotentialSyntaxNode {
    override fun getCompletions(cursor: Int, context: CompletionContext) = this@offsetPotentialOutput.getCompletions(cursor, context)?.thenApply { completions ->
        completions.map { completion ->
            completion.textEdit = completion.textEdit?.map({ left ->
                left.range = offset.offsetRange(left.range)
                Either.forLeft(left)
            }, { right ->
                right.insert = offset.offsetRange(right.insert)
                right.replace = offset.offsetRange(right.replace)
                Either.forRight(right)
            })
            completion.additionalTextEdits = completion.additionalTextEdits?.map { edit ->
                edit.range = offset.offsetRange(edit.range)
                edit
            }
            completion
        }
    }
}

fun PotentialSyntaxNode.withUniqueCompletions() = object : PotentialSyntaxNode {
    override fun getCompletions(cursor: Int, context: CompletionContext): CompletableFuture<List<CompletionItem>>? {
        val completionFuture = this@withUniqueCompletions.getCompletions(cursor, context)
        return completionFuture?.thenApply { it.distinct() }
    }
}

fun PotentialSyntaxNode.filterPotentialCursor(cursorPredicate: (Int) -> Boolean) = object : PotentialSyntaxNode {
    override fun getCompletions(cursor: Int, context: CompletionContext) =
        if(!cursorPredicate(cursor)) null
        else this@filterPotentialCursor.getCompletions(cursor, context)
}