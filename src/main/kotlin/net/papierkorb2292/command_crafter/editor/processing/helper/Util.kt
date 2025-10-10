package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.serialization.DynamicOps
import net.minecraft.registry.RegistryOps
import net.minecraft.util.packrat.ParsingRules
import net.minecraft.util.packrat.Symbol
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.mixin.editor.processing.ForwardingDynamicOpsAccessor
import net.papierkorb2292.command_crafter.mixin.packrat.ParsingRulesAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
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
        if(other.line != oneBasedOffset) other.character
        else character + other.character - oneBasedOffset
    )
}

fun Range.offsetBy(other: Position, zeroBased: Boolean = true): Range {
    return Range(start.offsetBy(other, zeroBased), end.offsetBy(other, zeroBased))
}
fun Position.offsetRange(other: Range, zeroBased: Boolean = true): Range {
    return Range(offsetBy(other.start, zeroBased), offsetBy(other.end, zeroBased))
}

operator fun Position.compareTo(other: Position): Int =
    if(line != other.line) line.compareTo(other.line)
    else character.compareTo(other.character)

/**
 * Makes sure the position is on the same line as requested so the completion is valid
 * (if the position was on a previous line, it will be moved to the start of this line,
 * if it was on a later line, it will be moved to the end of this line)
 *
 * Also, if the requested cursor is in a cursor mapping, the position will be moved inside
 * a mapping as well (for example, this means the completion won't override the \ at the end of vanilla commands)
 */
fun Position.clampCompletionToCursor(requestedLine: Int, requestedSourceCursor: Int, mappingInfo: FileMappingInfo, zeroBased: Boolean = true): Position {
    if(line == requestedLine)
        return this

    val hasMapping = mappingInfo.cursorMapper.containsSourceCursor(requestedSourceCursor, true)

    if(line <= requestedLine) {
        val lineStart = Position(requestedLine, 0)
        if(!hasMapping)
            return lineStart
        // Find the first mapping in the line
        val lineStartCursor = AnalyzingResult.getCursorFromPosition(lineStart, mappingInfo, zeroBased)
        var mappingIndex = mappingInfo.cursorMapper.sourceCursors.binarySearch { index ->
            if(mappingInfo.cursorMapper.sourceCursors[index] > lineStartCursor) 1
            else if (mappingInfo.cursorMapper.sourceCursors[index] + mappingInfo.cursorMapper.lengths[index] <= lineStartCursor) -1
            else 0
        }
        if(mappingIndex >= 0)
            // This mapping directly contains the start of the line
            return lineStart
        // No mapping contains the start of the line, use the start of the next mapping instead (it must be in the same line since requestedSourceCursor does have a mapping)
        mappingIndex = -(mappingIndex + 1)
        return AnalyzingResult.getPositionFromCursor(mappingInfo.cursorMapper.sourceCursors[mappingIndex], mappingInfo, zeroBased)
    }

    val oneBasedOffset = if(zeroBased) 0 else 1
    val lineEnd = Position(requestedLine, mappingInfo.lines[requestedLine - oneBasedOffset].length + oneBasedOffset)
    if(!hasMapping)
        return lineEnd
    val lineEndCursor = AnalyzingResult.getCursorFromPosition(lineEnd, mappingInfo, zeroBased)
    var mappingIndex = mappingInfo.cursorMapper.sourceCursors.binarySearch { index ->
        if(mappingInfo.cursorMapper.sourceCursors[index] > lineEndCursor) 1
        else if (mappingInfo.cursorMapper.sourceCursors[index] + mappingInfo.cursorMapper.lengths[index] <= lineEndCursor) -1
        else 0
    }
    if(mappingIndex >= 0)
        // This mapping directly contains the end of the line
        return lineEnd
    // No mapping contains the end of the line, use the end of the previous mapping instead (it must be in the same line since requestedSourceCursor does have a mapping)
    mappingIndex = -(mappingIndex + 2)
    return AnalyzingResult.getPositionFromCursor(mappingInfo.cursorMapper.sourceCursors[mappingIndex] + mappingInfo.cursorMapper.lengths[mappingIndex], mappingInfo, zeroBased)
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

fun StringRange.compareToExclusive(range: StringRange): Int {
    if(this.end <= range.start) return -1
    if(this.start >= range.end) return 1
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

data class CompletionItemPositionInfo(val processedCursor: Int, val requestedCursor: Int)

fun Suggestion.toCompletionItem(reader: DirectiveStringReader<AnalyzingResourceCreator>, completionCursorLine: Int, completionAbsoluteCursor: Int): CompletionItem {
    fun toPosition(cursor: Int): Position {
        val positionInfo = CompletionItemPositionInfo(cursor, completionAbsoluteCursor)
        val cache = reader.fileMappingInfo.completionItemToPositionFIFOCache
        val cached = cache.getAndMoveToLast(positionInfo)
        if(cached != null)
            return cached
        val pos = AnalyzingResult.getPositionFromCursor(
            reader.cursorMapper.mapToSource(cursor + reader.readSkippingChars),
            reader.fileMappingInfo
        ).clampCompletionToCursor(completionCursorLine, completionAbsoluteCursor, reader.fileMappingInfo)
        if(cache.size >= 7)
            cache.removeFirst()
        cache.put(positionInfo, pos)
        return pos
    }

    val replaceEndCursor = (this as SuggestionReplaceEndContainer).`command_crafter$getReplaceEnd`()
    return CompletionItem().apply {
        label = text
        detail = tooltip?.string
        val insertRange = Range(
            toPosition(range.start),
            toPosition(range.end)
        )
        textEdit = if(replaceEndCursor != null) Either.forRight(
            InsertReplaceEdit(
                text,
                insertRange,
                Range(
                    insertRange.start,
                    toPosition(replaceEndCursor)
                )
            )
        ) else Either.forLeft(
            TextEdit(insertRange, text)
        )
    }
}

fun createCursorMapperForEscapedCharacters(sourceString: String, startSourceCursor: Int): SplitProcessedInputCursorMapper {
    val cursorMapper = SplitProcessedInputCursorMapper()
    // Map cursors before the start to negative values such that there are no problems
    // when combining the cursor mappers (otherwise mappings for previous cursors could
    // end up within the string range and cause the mappings to be out of order)
    cursorMapper.addMapping(0, -startSourceCursor, startSourceCursor)
    var sourceIndex = 0
    var consumedEscapedCharacterCount = 0
    while(sourceIndex < sourceString.length) {
        if(sourceString[sourceIndex] != '\\') {
            sourceIndex++
            continue
        }
        val escapedCharacterCount =
            if(sourceString[sourceIndex + 1] == 'u') 5
            else 1
        cursorMapper.addFollowingMapping(
            cursorMapper.prevTargetEnd + consumedEscapedCharacterCount + startSourceCursor,
            sourceIndex - consumedEscapedCharacterCount + 1 - cursorMapper.prevTargetEnd
        )
        cursorMapper.addExpandedChar(sourceIndex + startSourceCursor, sourceIndex + escapedCharacterCount + startSourceCursor)
        consumedEscapedCharacterCount += escapedCharacterCount
        sourceIndex += escapedCharacterCount + 1
    }
    cursorMapper.addFollowingMapping(
        cursorMapper.prevTargetEnd + consumedEscapedCharacterCount  + startSourceCursor,
        sourceString.length - cursorMapper.prevTargetEnd
    )
    return cursorMapper
}

/**
 * Wraps the given dynamic ops with the given wrapper function and returns the wrapped ops.
 *
 * Since Minecraft sometimes does an *instanceof* check for RegistryOps,
 * it needs to be preserved as the outermost wrapper. Because of that,
 * the wrapped ops are returned alongside another DynamicOps instance
 * that should be used for en-/decoding. This second instance will still be a RegistryOps
 * instance if the input is one, but the delegate of the RegistryOps will be exchanged for
 * the new wrapped ops.
 */
inline fun <TData, TWrappedOps: DynamicOps<TData>> wrapDynamicOps(
    delegate: DynamicOps<TData>,
    wrapper: (DynamicOps<TData>) -> TWrappedOps,
): Pair<TWrappedOps, DynamicOps<TData>> {
    if(delegate is RegistryOps) {
        @Suppress("UNCHECKED_CAST")
        val wrappedOps = wrapper((delegate as ForwardingDynamicOpsAccessor).delegate as DynamicOps<TData>)
        return wrappedOps to delegate.withDelegate(wrappedOps)
    }
    val wrappedOps = wrapper(delegate)
    return wrappedOps to wrappedOps
}

// This method must be used instead of creating a new symbol with the same name, because ParsingRules uses an IdentityHashMap
fun <S> ParsingRules<S>.getSymbolByName(name: String): Symbol<*>? {
    @Suppress("UNCHECKED_CAST")
    return (this as ParsingRulesAccessor<S>).rules.keys
        .firstOrNull { it.name == name }
}