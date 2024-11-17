package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.CompletionItem
import java.util.concurrent.CompletableFuture

interface ContextCompletionProvider {
    fun getCompletions(fullInput: DirectiveStringReader<AnalyzingResourceCreator>): CompletableFuture<List<CompletionItem>>
}