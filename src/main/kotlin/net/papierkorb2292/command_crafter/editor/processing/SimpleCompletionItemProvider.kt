package net.papierkorb2292.command_crafter.editor.processing

import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import kotlin.math.min

class SimpleCompletionItemProvider(
    private val text: String,
    private val insertStart: Int,
    private val replaceEndProvider: () -> Int,
    private val mappingInfo: FileMappingInfo,
    private val languageServer: MinecraftLanguageServer,
    private val label: String = text,
    private val kind: CompletionItemKind? = null,
) : (Int) -> CompletionItem {
    override fun invoke(offset: Int): CompletionItem {
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
            Position(insertStartPos.line, 0)
        } else {
            insertStartPos
        }
        if(!languageServer.clientCapabilities!!.textDocument.completion.completionItem.insertReplaceSupport) {
            return CompletionItem().also {
                it.label = label
                it.kind = kind
                it.sortText = " $label" // Add whitespace so it appears above VSCodes suggestions
                it.textEdit = Either.forLeft(TextEdit(Range(clampedInsertStartPos, insertEndPos), text))
            }
        }
        val replaceEndPos = AnalyzingResult.getPositionFromCursor(
            mappingInfo.cursorMapper.mapToSource(replaceEndProvider() + mappingInfo.readSkippingChars),
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