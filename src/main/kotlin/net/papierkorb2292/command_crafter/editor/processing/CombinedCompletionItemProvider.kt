package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCompletionProvider
import org.eclipse.lsp4j.CompletionItem
import java.util.concurrent.CompletableFuture

class CombinedCompletionItemProvider(
    private val providers: List<AnalyzingCompletionProvider>
) : AnalyzingCompletionProvider {
    override fun invoke(offset: Int): CompletableFuture<List<CompletionItem>> {
        val futures = providers.map { it(offset) }
        return CompletableFuture.allOf(*futures.toTypedArray())
            .thenApply { futures.flatMap { it.get() } }
    }
}