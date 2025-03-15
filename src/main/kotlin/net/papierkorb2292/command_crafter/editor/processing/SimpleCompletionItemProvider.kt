package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCompletionProvider
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import kotlin.math.min

class SimpleCompletionItemProvider(
    private val text: String,
    private val insertStart: Int,
    private val replaceEndProvider: () -> Int?,
    private val mappingInfo: FileMappingInfo,
    private val label: String = text,
    private val kind: CompletionItemKind? = null,
) : AnalyzingCompletionProvider {
    override fun invoke(offset: Int): CompletableFuture<List<CompletionItem>>
        = CompletableFuture.completedFuture(listOf(createCompletionItem(offset)))

    private fun createCompletionItem(offset: Int): CompletionItem {
        // Adjusting the insert start if the cursor is before the insert start
        val adjustedInsertStart = min(insertStart + mappingInfo.readSkippingChars, offset)
        val insertStartPos = AnalyzingResult.getPositionFromCursor(
            mappingInfo.cursorMapper.mapToSource(adjustedInsertStart),
            mappingInfo
        )
        val insertEndPos = AnalyzingResult.getPositionFromCursor(
            mappingInfo.cursorMapper.mapToSource(offset),
            mappingInfo
        )
        val clampedInsertStartPos = if(insertStartPos.line < insertEndPos.line) {
            Position(insertEndPos.line, 0)
        } else {
            insertStartPos
        }

        val replaceEnd = replaceEndProvider()
            ?: return CompletionItem().also {
                it.label = label
                it.kind = kind
                it.sortText = " $label" // Add whitespace so it appears above VSCodes suggestions
                it.textEdit = Either.forLeft(TextEdit(Range(clampedInsertStartPos, insertEndPos), text))
            }

        val replaceEndPos = AnalyzingResult.getPositionFromCursor(
            mappingInfo.cursorMapper.mapToSource(replaceEnd + mappingInfo.readSkippingChars),
            mappingInfo
        )
        val clampedReplaceEndPos = if(replaceEndPos.line > insertEndPos.line) {
            Position(insertEndPos.line, mappingInfo.lines[insertEndPos.line].length)
        } else {
            replaceEndPos
        }
        return CompletionItem().also {
            it.label = label
            it.filterText = text
            it.sortText = " $label" // Add whitespace so it appears above VSCodes suggestions
            it.kind = kind
            it.textEdit = Either.forRight(
                InsertReplaceEdit(
                    text,
                    Range(clampedInsertStartPos, insertEndPos),
                    Range(clampedInsertStartPos, clampedReplaceEndPos)
                )
            )
        }
    }
}