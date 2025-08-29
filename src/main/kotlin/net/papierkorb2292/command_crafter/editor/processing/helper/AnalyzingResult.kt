package net.papierkorb2292.command_crafter.editor.processing.helper

import com.fasterxml.jackson.annotation.JsonIgnore
import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.FeatureConfig
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import kotlin.math.min

class AnalyzingResult(val mappingInfo: FileMappingInfo, val semanticTokens: SemanticTokensBuilder, val diagnostics: MutableList<Diagnostic> = mutableListOf(), val filePosition: Position, var documentation: String? = null) {

    constructor(reader: FileMappingInfo, filePosition: Position, diagnostics: MutableList<Diagnostic> = mutableListOf()) : this(reader, SemanticTokensBuilder(reader), diagnostics, filePosition)

    private val completionProviders: MutableMap<String, MutableList<RangedDataProvider<CompletableFuture<List<CompletionItem>>>>> = mutableMapOf()
    private val hoverProviders: MutableList<RangedDataProvider<CompletableFuture<Hover>>> = mutableListOf()
    private val definitionProviders: MutableList<RangedDataProvider<CompletableFuture<Either<List<Location>, List<LocationLink>>>>> = mutableListOf()

    fun combineWith(other: AnalyzingResult) {
        combineWithExceptCompletions(other)
        combineWithCompletionProviders(other)
    }

    fun combineWithExceptCompletions(other: AnalyzingResult) {
        semanticTokens.combineWith(other.semanticTokens)
        diagnostics += other.diagnostics
        addRangedDataProviders(hoverProviders, other.hoverProviders)
        addRangedDataProviders(definitionProviders, other.definitionProviders)
    }

    fun combineWithCompletionProviders(other: AnalyzingResult, channelSuffix: String = "") {
        for((channel, providers) in other.completionProviders) {
            addRangedDataProviders(getOrPutCompletionProvidersForChannel(channel + channelSuffix), providers)
        }
    }

    fun addCompletionProvider(
        completionChannelName: String,
        provider: RangedDataProvider<CompletableFuture<List<CompletionItem>>>,
        shouldMap: Boolean
    ) {
        val channel = getOrPutCompletionProvidersForChannel(completionChannelName)
        if(shouldMap) {
            addMappedRangedDataProvider(channel, provider)
            return
        }
        addRangedDataProvider(channel, provider)
    }

    /**
     * This applies [ProcessedInputCursorMapper.mapToSource] only on the start and end of the provider range after adding readSkippingChars,
     * whereas [addCompletionProvider] with shouldMap=true would split the range up into multiple parts that fit the mapping.
     *
     * The cursor given to the data provider will be the absolute position in the file.
     */
    fun addCompletionProviderWithContinuosMapping(
        completionChannelName: String,
        provider: RangedDataProvider<CompletableFuture<List<CompletionItem>>>
    ) {
        val mappedStart = getEarliestSourceCursorWithInclusiveEndMapping(provider.cursorRange.start + mappingInfo.readSkippingChars)
        //val mappedStart = mappingInfo.cursorMapper.mapToSource(provider.cursorRange.start + mappingInfo.readSkippingChars)
        val mappedEnd = mappingInfo.cursorMapper.mapToSource(provider.cursorRange.end + mappingInfo.readSkippingChars)
        addCompletionProvider(
            completionChannelName,
            RangedDataProvider(
                StringRange(mappedStart, mappedEnd),
                provider.dataProvider
            ),
            false
        )
    }

    private fun getEarliestSourceCursorWithInclusiveEndMapping(targetCursor: Int): Int {
        // Find the matching mapping with exclusive end like normal
        var mappingIndex = mappingInfo.cursorMapper.targetCursors.binarySearch { index ->
            if(mappingInfo.cursorMapper.targetCursors[index] > targetCursor) 1
            else if (mappingInfo.cursorMapper.targetCursors[index] + mappingInfo.cursorMapper.lengths[index] <= targetCursor) -1
            else 0
        }
        if(mappingIndex < 0) {
            if(mappingIndex == -1) {
                return targetCursor
            }
            mappingIndex = -(mappingIndex + 2)
        }

        // Find the earliest mapping where an inclusive end includes the target cursor
        while(mappingIndex > 0 && mappingInfo.cursorMapper.targetCursors[mappingIndex - 1] + mappingInfo.cursorMapper.lengths[mappingIndex - 1] >= targetCursor)
            mappingIndex--

        val startInputCursor = mappingInfo.cursorMapper.targetCursors[mappingIndex]
        val relativeCursor = targetCursor - startInputCursor
        return mappingInfo.cursorMapper.sourceCursors[mappingIndex] + relativeCursor
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

    fun getCompletionProviderForCursor(filterCursor: Int): RangedDataProvider<CompletableFuture<List<CompletionItem>>>? {
        val providers = completionProviders.mapNotNull { getRangedDataProviderForCursor(it.value, filterCursor, true) }
        if(providers.isEmpty())
            return null
        val completeStringRange = providers.asSequence().map { it.cursorRange }.reduce(StringRange::encompassing)
        return RangedDataProvider(completeStringRange) { providerCursor ->
            val completions = providers.map { it.dataProvider(providerCursor) }.toTypedArray()
            CompletableFuture.allOf(*completions).thenApply { completions.flatMap { it.join() } }
        }
    }

    fun getHoverProviderForCursor(cursor: Int) =
        getRangedDataProviderForCursor(hoverProviders, cursor)

    fun getDefinitionProviderForCursor(cursor: Int) =
        getRangedDataProviderForCursor(definitionProviders, cursor) ?: getRangedDataProviderForCursor(definitionProviders, cursor - 1)

    fun cutAfterTargetCursor(targetCursor: Int) {
        cutAfterSourceCursor(mappingInfo.cursorMapper.mapToSource(targetCursor + mappingInfo.readSkippingChars))
    }

    fun cutAfterSourceCursor(sourceCursor: Int) {
        val position = getPositionFromCursor(sourceCursor, mappingInfo)
        semanticTokens.cutAfter(position)
        cutRangedDataProviderAfterCursor(hoverProviders, sourceCursor)
        cutRangedDataProviderAfterCursor(definitionProviders, sourceCursor)
        completionProviders.values.forEach {
            cutRangedDataProviderAfterCursor(it, sourceCursor)
        }
    }

    private fun <TData> cutRangedDataProviderAfterCursor(providers: MutableList<RangedDataProvider<TData>>, sourceCursor: Int) {
        var providerIndex = providers.binarySearch { -sourceCursor.compareTo(it.cursorRange) }
        if(providerIndex >= 0) {
            val provider = providers[providerIndex]
            providers[providerIndex] = RangedDataProvider(StringRange(provider.cursorRange.start, sourceCursor), provider.dataProvider)
        } else {
            providerIndex = -providerIndex - 1
        }
        providers.subList(providerIndex, providers.size).clear()
    }

    fun copyInput(): AnalyzingResult {
        val newMappingInfo = mappingInfo.copy()
        return AnalyzingResult(newMappingInfo, SemanticTokensBuilder(newMappingInfo), mutableListOf(), filePosition, documentation)
    }
    fun copy() = copyInput().also {
        it.combineWith(this)
    }

    fun copyExceptCompletions() = copyInput().also {
        it.combineWithExceptCompletions(this)
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

    private fun getOrPutCompletionProvidersForChannel(channel: String) =
        completionProviders.getOrPut(channel, ::mutableListOf)

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
            getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(startCursor), mappingInfo),
            getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(endCursor), mappingInfo)
        )
    }

    companion object {
        const val LANGUAGE_COMPLETION_CHANNEL = "language"
        const val DIRECTIVE_COMPLETION_CHANNEL = "directive"

        fun getPositionFromCursor(cursor: Int, mappingInfo: FileMappingInfo, zeroBased: Boolean = true): Position {
            val cached = mappingInfo.positionFromCursorFIFOCache.getAndMoveToLast(cursor)
            if(cached != null)
                return cached

            val oneBasedOffset = if(zeroBased) 0 else 1
            var lineIndex = mappingInfo.accumulatedLineLengths.binarySearch { index ->
                mappingInfo.accumulatedLineLengths[index].compareTo(cursor)
            }
            if(lineIndex < 0) {
                // No line has the exact accumulated length, so select the previous line
                lineIndex = -lineIndex - 2
            }
            val pos =if (lineIndex == -1) {
                // Position is on the first line
                Position(oneBasedOffset, cursor)
            } else {
                val accumulatedLineLength = mappingInfo.accumulatedLineLengths[lineIndex]
                Position(
                    // Adds one to lineIndex, because for any index accumulatedLineLengths counts the characters to the
                    // start of the next line, so the actual line that the position is on is also the next line
                    lineIndex + 1 + oneBasedOffset,
                    cursor - accumulatedLineLength + oneBasedOffset
                )
            }

            if(mappingInfo.positionFromCursorFIFOCache.size >= 7)
                mappingInfo.positionFromCursorFIFOCache.removeFirst()
            mappingInfo.positionFromCursorFIFOCache.put(cursor, pos)
            return pos
        }

        @Deprecated("Replaced with an overload using FileMappingInfo for better performance")
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

        fun getCursorFromPosition(position: Position, mappingInfo: FileMappingInfo, zeroBased: Boolean = true): Int {
            val oneBasedOffset = if(zeroBased) 0 else 1
            if(position.line - oneBasedOffset == 0)
                return position.character
            return mappingInfo.accumulatedLineLengths[position.line - 1 - oneBasedOffset] + position.character - oneBasedOffset
        }

        @Deprecated("Replaced with an overload using FileMappingInfo for better performance")
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

    class RangedDataProvider<out TData>(
        val cursorRange: StringRange,
        @JsonIgnore // Prevents some self references, because the provider could refer back to AnalyzingResult, and it probably just contains some cryptic stuff anyway
        val dataProvider: AnalyzingDataProvider<TData>
    ) {
        init {
            if(cursorRange.start > cursorRange.end) {
                throw IllegalArgumentException("Start cursor must not be greater than end cursor")
            }
        }
    }
}

typealias AnalyzingDataProvider<TData> = (Int) -> TData
typealias AnalyzingCompletionProvider = AnalyzingDataProvider<CompletableFuture<List<CompletionItem>>>
typealias AnalyzingHoverProvider = AnalyzingDataProvider<CompletableFuture<Hover>>
typealias AnalyzingDefinitionProvider = AnalyzingDataProvider<CompletableFuture<Either<List<Location>, List<LocationLink>>>>
