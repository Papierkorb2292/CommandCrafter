package net.papierkorb2292.command_crafter.editor.processing.helper

import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.FeatureConfig
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import kotlin.math.min

class AnalyzingResult(val mappingInfo: FileMappingInfo, val semanticTokens: SemanticTokensBuilder, val diagnostics: MutableList<Diagnostic> = mutableListOf(), val filePosition: Position, var documentation: String? = null) {
    constructor(reader: FileMappingInfo, filePosition: Position, diagnostics: MutableList<Diagnostic> = mutableListOf()) : this(reader, SemanticTokensBuilder(reader), diagnostics, filePosition)

    private val completionProviders: MutableList<RangedDataProvider<CompletableFuture<List<CompletionItem>>>> = mutableListOf()
    private val hoverProviders: MutableList<RangedDataProvider<CompletableFuture<Hover>>> = mutableListOf()
    private val definitionProviders: MutableList<RangedDataProvider<CompletableFuture<Either<List<Location>, List<LocationLink>>>>> = mutableListOf()

    fun combineWith(other: AnalyzingResult) {
        combineWithExceptCompletions(other)
        addRangedDataProviders(completionProviders, other.completionProviders)
    }

    fun combineWithExceptCompletions(other: AnalyzingResult) {
        semanticTokens.combineWith(other.semanticTokens)
        diagnostics += other.diagnostics
        addRangedDataProviders(hoverProviders, other.hoverProviders)
        addRangedDataProviders(definitionProviders, other.definitionProviders)
    }

    fun combineWithCompletionProviders(other: AnalyzingResult) {
        addRangedDataProviders(completionProviders, other.completionProviders)
    }

    fun addCompletionProvider(provider: RangedDataProvider<CompletableFuture<List<CompletionItem>>>, shouldMap: Boolean) {
        if(shouldMap) {
            addMappedRangedDataProvider(completionProviders, provider)
            return
        }
        addRangedDataProvider(completionProviders, provider)
    }

    fun addHoverProvider(provider: RangedDataProvider<CompletableFuture<Hover>>, shouldMap: Boolean) {
        if(shouldMap) {
            addMappedRangedDataProvider(hoverProviders, provider)
            return
        }
        addRangedDataProvider(hoverProviders, provider)
    }

    fun addDefinitionProvider(provider: RangedDataProvider<CompletableFuture<Either<List<Location>, List<LocationLink>>>>, shouldMap: Boolean) {
        if(shouldMap) {
            addMappedRangedDataProvider(definitionProviders, provider)
            return
        }
        addRangedDataProvider(definitionProviders, provider)
    }

    fun getCompletionProviderForCursor(cursor: Int) =
        getRangedDataProviderForCursor(completionProviders, cursor, true)

    fun getHoverProviderForCursor(cursor: Int) =
        getRangedDataProviderForCursor(hoverProviders, cursor)

    fun getDefinitionProviderForCursor(cursor: Int) =
        getRangedDataProviderForCursor(definitionProviders, cursor) ?: getRangedDataProviderForCursor(definitionProviders, cursor - 1)

    fun copyInput(): AnalyzingResult {
        val newMappingInfo = mappingInfo.copy()
        return AnalyzingResult(newMappingInfo, SemanticTokensBuilder(newMappingInfo), mutableListOf(), filePosition, documentation)
    }
    fun copy() = copyInput().also {
        it.combineWith(this)
    }

    fun clearDisabledFeatures(featureConfig: FeatureConfig, analyzerNameInserts: List<String>) {
        if(!featureConfig.isEnabled(analyzerNameInserts.map { "analyzer$it.completions" }, true))
            completionProviders.clear()
        if(!featureConfig.isEnabled(analyzerNameInserts.map { "analyzer$it.hovers" }, true))
            hoverProviders.clear()
        if(!featureConfig.isEnabled(analyzerNameInserts.map { "analyzer$it.definitions" }, true))
            definitionProviders.clear()
        if(!featureConfig.isEnabled(analyzerNameInserts.map { "analyzer$it.diagnostics" }, true))
            diagnostics.clear()
        if(!featureConfig.isEnabled(analyzerNameInserts.map { "analyzer$it.semanticTokens" }, true))
            semanticTokens.clear()
    }

    private fun <TData> addRangedDataProviders(dest: MutableList<RangedDataProvider<TData>>, source: List<RangedDataProvider<TData>>) {
        for(provider in source) {
            addRangedDataProvider(dest, provider)
        }
    }

    private fun <TData> addRangedDataProvider(dest: MutableList<RangedDataProvider<TData>>, provider: RangedDataProvider<TData>) {
        checkCanAddRangedDataProvider(dest, provider)
        dest.add(provider)
    }

    private fun <TData> addMappedRangedDataProvider(dest: MutableList<RangedDataProvider<TData>>, unmappedProvider: RangedDataProvider<TData>) {
        val startCursor = unmappedProvider.cursorRange.start + mappingInfo.readSkippingChars

        val cursorMapper = mappingInfo.cursorMapper

        var mappingIndex = cursorMapper.targetCursors.binarySearch { index ->
            if(cursorMapper.targetCursors[index] + cursorMapper.lengths[index] <= startCursor) -1
            else if (cursorMapper.targetCursors[index] > startCursor) 1
            else 0
        }
        if(mappingIndex < 0) {
            mappingIndex = -(mappingIndex + 2)
        }
        var mappingRelativeCursor = startCursor
        if(mappingIndex >= 0) {
            mappingRelativeCursor -= cursorMapper.targetCursors[mappingIndex]
        }

        var remainingLength = unmappedProvider.cursorRange.length
        while(mappingIndex < cursorMapper.targetCursors.size) {
            val remainingLengthCoveredByMapping =
                if(mappingIndex >= 0 && mappingRelativeCursor < cursorMapper.lengths[mappingIndex])
                    min(remainingLength, cursorMapper.lengths[mappingIndex] - mappingRelativeCursor)
                else
                    remainingLength
            val mappingAbsoluteStart =
                if(mappingIndex >= 0) cursorMapper.sourceCursors[mappingIndex] + mappingRelativeCursor
                else mappingRelativeCursor
            val mappedStartPosition = startCursor + unmappedProvider.cursorRange.length - remainingLength
            addRangedDataProvider(dest, RangedDataProvider(StringRange(mappingAbsoluteStart, mappingAbsoluteStart + remainingLengthCoveredByMapping)) {
                val mappingRelative = it - mappingAbsoluteStart
                unmappedProvider.dataProvider(mappingRelative + mappedStartPosition)
            })

            if(remainingLengthCoveredByMapping >= remainingLength)
                break

            remainingLength -= remainingLengthCoveredByMapping
            mappingRelativeCursor = 0
            mappingIndex++
        }
    }

    /**
     * Check if the new provider is completely after the last provider in the list.
     *
     * This allows a binary search to later be performed on the list.
     */
    private fun checkCanAddRangedDataProvider(dest: List<RangedDataProvider<*>>, provider: RangedDataProvider<*>) {
        val last = dest.lastOrNull() ?: return
        if(last.cursorRange.end > provider.cursorRange.start) {
            throw IllegalArgumentException("Ranged data providers must be added in order and not overlap")
        }
    }

    private fun <TData> getRangedDataProviderForCursor(providers: List<RangedDataProvider<TData>>, cursor: Int, inclusiveRangeEnd: Boolean = false): RangedDataProvider<TData>? {
        val index = providers.binarySearch {
            if(cursor < it.cursorRange.start) 1
            else if(cursor > it.cursorRange.end || (!inclusiveRangeEnd && cursor == it.cursorRange.end)) -1
            else 0
        }
        return if(index >= 0) {
            if(inclusiveRangeEnd && index + 1 < providers.size && cursor == providers[index + 1].cursorRange.start) {
                providers[index + 1]
            } else providers[index]
        } else null
    }

    fun toFileRange(stringRange: StringRange): Range {
        val startCursor = stringRange.start + mappingInfo.readSkippingChars
        val endCursor = stringRange.end + mappingInfo.readSkippingChars
        return Range(
            getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(startCursor), mappingInfo.lines),
            getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(endCursor), mappingInfo.lines)
        )
    }

    companion object {
        fun getPositionFromCursor(cursor: Int, mappingInfo: FileMappingInfo, zeroBased: Boolean = true) =
            getPositionFromCursor(cursor, mappingInfo.lines, zeroBased)

        fun getPositionFromCursor(cursor: Int, lines: List<String>, zeroBased: Boolean = true): Position {
            if(lines.isEmpty()) return Position()
            var charactersLeft = cursor
            for((index, line) in lines.withIndex()) {
                val length = line.length + 1
                if(charactersLeft < length) {
                    return if(zeroBased) Position(index, charactersLeft)
                    else Position(index + 1, charactersLeft + 1)
                }
                charactersLeft -= length
            }
            val lastLineNumber = lines.size
            val lastColumnNumber = lines.last().length
            return if(zeroBased) Position(lastLineNumber - 1, lastColumnNumber)
            else Position(lastLineNumber, lastColumnNumber + 1)
        }

        fun getCursorFromPosition(position: Position, mappingInfo: FileMappingInfo, zeroBased: Boolean = true) =
            getCursorFromPosition(mappingInfo.lines, position, zeroBased)

        fun getCursorFromPosition(lines: List<String>, position: Position, zeroBased: Boolean = true): Int {
            if(lines.isEmpty()) return 0
            var cursor = 0
            val lineIndex = if(zeroBased) position.line else position.line - 1
            for((index, line) in lines.withIndex()) {
                if(index == lineIndex) {
                    return cursor + position.character
                }
                cursor += line.length + 1
            }
            return cursor
        }

        inline fun getInlineRangesBetweenCursors(startCursor: Int, endCursor: Int, lines: List<String>, rangeConsumer: (line: Int, cursor: Int, length: Int) -> Unit) {
            var startCharactersLeft = startCursor
            var line = 0
            while(lines.size > line && startCharactersLeft >= lines[line].length + 1) {
                startCharactersLeft -= lines[line++].length + 1
            }
            var rangeCharactersLeft = endCursor - startCursor
            while(lines.size > line && rangeCharactersLeft > 0) {
                val lineLength = lines[line].length + 1
                if(lineLength >= rangeCharactersLeft + startCharactersLeft) {
                    rangeConsumer(line, startCharactersLeft, rangeCharactersLeft)
                    return
                }
                rangeConsumer(line, startCharactersLeft, lineLength)
                line++
                rangeCharactersLeft -= lineLength - startCharactersLeft
                startCharactersLeft = 0
            }
        }

        fun getLineCursorRange(lineNumber: Int, mappingInfo: FileMappingInfo) =
            getLineCursorRange(lineNumber, mappingInfo.lines)

        fun getLineCursorRange(lineNumber: Int, lines: List<String>): StringRange {
            var cursor = 0
            val lineIndex = lineNumber - 1
            for((index, currentText) in lines.withIndex()) {
                if(index == lineIndex) {
                    return StringRange(cursor, cursor + currentText.length)
                }
                cursor += currentText.length + 1
            }
            return StringRange(cursor, cursor)
        }
    }

    class RangedDataProvider<out TData>(val cursorRange: StringRange, val dataProvider: (Int) -> TData) {
        init {
            if(cursorRange.start > cursorRange.end) {
                throw IllegalArgumentException("Start cursor must not be greater than end cursor")
            }
        }
    }
}