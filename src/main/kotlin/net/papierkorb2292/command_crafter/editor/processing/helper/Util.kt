package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.*

fun Position.advance() = advance(1)
fun Position.advance(amount: Int) = Position(line, character + amount)

operator fun Int.compareTo(range: StringRange): Int {
    if(this < range.start) return -1
    if(this >= range.end) return 1
    return 0
}

operator fun StringRange.compareTo(range: StringRange): Int {
    if(this.end < range.start) return -1
    if(this.start > range.end) return 1
    return 0
}

fun getKeywordsFromPath(path: String) =
    path.split('/', '_').map { standardizeKeyword(it) }

fun standardizeKeyword(keyword: String): String {
    val lower = keyword.lowercase(Locale.getDefault())
    return if(lower == "atlases") "atlas"
    else if(lower.endsWith("s")) lower.dropLast(1)
    else lower
}

fun Suggestion.toCompletionItem(reader: DirectiveStringReader<AnalyzingResourceCreator>, replaceEndCursor: Int?): CompletionItem {
    if(replaceEndCursor != null) {
        toInsertReplaceCompletionItem(reader, replaceEndCursor)?.let {
            return it
        }
    }
    return toTextEditCompletionItem(reader)
}

fun Suggestion.toTextEditCompletionItem(reader: DirectiveStringReader<AnalyzingResourceCreator>) =
    CompletionItem().apply {
        label = text
        detail = tooltip?.string
        textEdit = Either.forLeft(TextEdit(
            Range(
                AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(range.start + reader.readSkippingChars), reader.lines),
                AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(range.end + reader.readSkippingChars), reader.lines)
            ),
            text
        ))
    }

fun Suggestion.toInsertReplaceCompletionItem(reader: DirectiveStringReader<AnalyzingResourceCreator>, replaceEndCursor: Int): CompletionItem? {
    val clientCapabilities = reader.resourceCreator.languageServer.clientCapabilities
    if(clientCapabilities == null || !clientCapabilities.textDocument.completion.completionItem.insertReplaceSupport)
        return null
    return CompletionItem().apply {
        label = text
        detail = tooltip.string
        val completionStartPos = AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(range.start + reader.skippingCursor), reader.lines)
        textEdit = Either.forRight(
            InsertReplaceEdit(
                text,
                Range(
                    completionStartPos,
                    AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(range.end + reader.skippingCursor), reader.lines)
                ),
                Range(
                    completionStartPos,
                    AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(replaceEndCursor + reader.skippingCursor), reader.lines)
                )
            )
        )
    }
}