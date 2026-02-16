package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.processing.helper.PotentialSyntaxNode
import org.eclipse.lsp4j.CompletionContext
import org.eclipse.lsp4j.CompletionItem
import java.util.concurrent.CompletableFuture

class CombinedPotentialSyntaxNode(
    private val providers: List<PotentialSyntaxNode>
) : PotentialSyntaxNode {
    override fun getCompletions(cursor: Int, context: CompletionContext): CompletableFuture<List<CompletionItem>> {
        val futures = providers.map { it.getCompletions(cursor, context) }
        return CompletableFuture.allOf(*futures.toTypedArray())
            .thenApply { futures.flatMap { it?.get() ?: emptyList() } }
    }
}