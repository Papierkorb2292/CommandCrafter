package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.processing.helper.PotentialSyntaxNode
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class StreamCompletionItemProvider(
    private val insertStart: Int,
    private val replaceEndProvider: () -> Int?,
    private val mappingInfo: FileMappingInfo,
    private val kind: CompletionItemKind?,
    private val completionsCallback: () -> Stream<Completion>,
) : PotentialSyntaxNode {
    override fun getCompletions(cursor: Int, context: CompletionContext?): CompletableFuture<List<CompletionItem>> {
        // Uses SimpleCompletionItemProvider to calculate the positions and such and then adjusts the text and label for each completion
        val base = SimpleCompletionItemProvider("", insertStart, replaceEndProvider, mappingInfo, kind = kind).createCompletionItem(cursor)
        return CompletableFuture.completedFuture(completionsCallback().map { completion ->
            CompletionItem().also {
                it.label = completion.label
                it.filterText = completion.text
                it.sortText = base.sortText + completion.label
                it.kind = kind
                it.textEdit = base.textEdit.map({ textEdit ->
                    Either.forLeft(TextEdit(textEdit.range, completion.text))
                }, { insertReplaceEdit ->
                    Either.forRight(InsertReplaceEdit(completion.text, insertReplaceEdit.insert, insertReplaceEdit.replace))
                })
                completion.completionModifier?.invoke(it)
            }
        }.toList())
    }

    data class Completion(val text: String, val label: String = text, val completionModifier: ((CompletionItem) -> Unit)? = null)
}