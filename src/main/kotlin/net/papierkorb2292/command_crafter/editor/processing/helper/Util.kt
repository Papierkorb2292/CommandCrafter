package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import net.minecraft.network.PacketByteBuf
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.*

fun Position.advance() = advance(1)
fun Position.advance(amount: Int) = Position(line, character + amount)
fun Position.advanceLine() = advanceLines(1)
fun Position.advanceLines(amount: Int) = Position(line + amount, 0)

fun Position.offsetBy(other: Position, zeroBased: Boolean = true): Position {
    val oneBasedOffset = if(zeroBased) 0 else 1
    return Position(
        line + other.line - oneBasedOffset,
        if(line != 0) character
        else character + other.character - oneBasedOffset
    )
}

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

fun Suggestion.toCompletionItem(reader: DirectiveStringReader<AnalyzingResourceCreator>): CompletionItem {
    val replaceEndCursor = (this as SuggestionReplaceEndContainer).`command_crafter$getReplaceEnd`()
    return CompletionItem().apply {
        label = text
        detail = tooltip?.string
        val insertRange = Range(
            AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(range.start + reader.readSkippingChars), reader.lines),
            AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(range.end + reader.readSkippingChars), reader.lines)
        )
        textEdit = if(replaceEndCursor != null) Either.forRight(
            InsertReplaceEdit(
                text,
                insertRange,
                Range(
                    insertRange.start,
                    AnalyzingResult.getPositionFromCursor(reader.cursorMapper.mapToSource(replaceEndCursor + reader.readSkippingChars), reader.lines)
                )
            )
        ) else Either.forLeft(
            TextEdit(insertRange, text)
        )
    }
}