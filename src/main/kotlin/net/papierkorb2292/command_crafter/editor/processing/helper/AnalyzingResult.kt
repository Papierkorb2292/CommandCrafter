package net.papierkorb2292.command_crafter.editor.processing.helper

import com.fasterxml.jackson.annotation.JsonIgnore
import com.mojang.brigadier.context.StringRange
import net.papierkorb2292.command_crafter.editor.FeatureConfig
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import net.papierkorb2292.command_crafter.helper.binarySearch
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import kotlin.math.min

class AnalyzingResult(
    val mappingInfo: FileMappingInfo,
    val semanticTokens: SemanticTokensBuilder,
    val diagnostics: MutableList<Diagnostic>,
    val colorInfos: MutableList<ColorInfo>,
    val filePosition: Position,
    var documentation: String?,
    private val actualSyntaxNodes: MutableList<RangedSyntaxNode<ActualSyntaxNode>>,
    private val finishedPotentialSyntaxNodes: MutableList<MutableList<RangedSyntaxNode<PotentialSyntaxNode>>>,
    private val buildingPotentialSyntaxNodes: MutableMap<String, MutableList<RangedSyntaxNode<PotentialSyntaxNode>>>,
) : ActualSyntaxNode, PotentialSyntaxNode {

    constructor(reader: FileMappingInfo, filePosition: Position) : this(
        reader,
        SemanticTokensBuilder(reader),
        mutableListOf(),
        mutableListOf(),
        filePosition,
        null,
        mutableListOf(),
        mutableListOf(),
        mutableMapOf(),
    )

    fun combineWith(other: AnalyzingResult) {
        combineWithActual(other)
        combineWithPotential(other)
    }

    fun combineWithActual(other: AnalyzingResult) {
        semanticTokens.combineWith(other.semanticTokens)
        diagnostics += other.diagnostics
        colorInfos += other.colorInfos
        addSyntaxNodes(actualSyntaxNodes, other.actualSyntaxNodes)
    }

    fun combineWithPotential(other: AnalyzingResult, channelSuffix: String = "") {
        for((channel, nodes) in other.buildingPotentialSyntaxNodes) {
            addSyntaxNodes(getOrPutPotentialSyntaxNodesForChannel(channel + channelSuffix), nodes)
        }
        finishedPotentialSyntaxNodes += other.finishedPotentialSyntaxNodes
    }

    fun combineWithPotentialFinished(other: AnalyzingResult) {
        finishedPotentialSyntaxNodes += other.finishedPotentialSyntaxNodes + other.buildingPotentialSyntaxNodes.values
    }

    fun addOffset(parent: AnalyzingResult, position: Position, cursorOffset: Int): AnalyzingResult {
        return AnalyzingResult(
            parent.mappingInfo,
            SemanticTokensBuilder(parent.mappingInfo).apply {
                combineWith(semanticTokens)
                offset(position)
            },
            diagnostics.mapTo(mutableListOf()) { original ->
                // Copy data. Original needs to stay the same because this method is used for caching
                Diagnostic().apply {
                    range = position.offsetRange(original.range)
                    severity = original.severity
                    code = original.code
                    codeDescription = original.codeDescription
                    source = original.source
                    message = original.message
                    tags = original.tags
                    relatedInformation = original.relatedInformation
                    data = original.data
                }
            },
            colorInfos.mapTo(mutableListOf()) { original ->
                // Copy data. Original needs to stay the same because this method is used for caching
                object : ColorInfo {
                    override val color = original.color
                    override val range = position.offsetRange(original.range)
                    override fun getPresentation(params: ColorPresentationParams): List<ColorPresentation> {
                        params.range = position.negate().offsetRange(params.range)
                        val presentations =  original.getPresentation(params)
                        for(presentation in presentations) {
                            if(presentation.textEdit != null)
                                presentation.textEdit.range = position.offsetRange(presentation.textEdit.range)
                            if(presentation.additionalTextEdits != null)
                                for(textEdit in presentation.additionalTextEdits) {
                                    textEdit.range = position.offsetRange(textEdit.range)
                                }
                        }
                        return presentations
                    }
                }
            },
            parent.filePosition,
            parent.documentation,
            getActualNodeCompressed(this.offsetActualInput(-cursorOffset).offsetActualOutput(position), cursorOffset),
            getPotentialNodeCompressed(this.offsetPotentialInput(-cursorOffset).offsetPotentialOutput(position), cursorOffset),
            mutableMapOf(),
        )
    }

    /**
     * This applies [ProcessedInputCursorMapper.mapToSource] only on the start and end of the provider range after adding readSkippingChars,
     * whereas [addMappedActualSyntaxNode] would split the range up into multiple parts that fit the mapping.
     *
     * The cursor given to the data provider will be the absolute position in the file.
     */
    fun addContinuouslyMappedPotentialSyntaxNode(
        channel: String,
        stringRange: StringRange,
        node: PotentialSyntaxNode
    ) {
        val mappedStart = getEarliestSourceCursorWithInclusiveEndMapping(stringRange.start + mappingInfo.readSkippingChars)
        val mappedEnd = mappingInfo.cursorMapper.mapToSource(stringRange.end + mappingInfo.readSkippingChars)
        addPotentialSyntaxNode(
            channel,
            StringRange(mappedStart, mappedEnd),
            node
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

    fun cutAfterTargetCursor(targetCursor: Int) {
        cutAfterSourceCursor(mappingInfo.cursorMapper.mapToSource(targetCursor + mappingInfo.readSkippingChars))
    }

    fun cutAfterSourceCursor(sourceCursor: Int) {
        val position = getPositionFromCursor(sourceCursor, mappingInfo)
        semanticTokens.cutAfter(position)
        cutSyntaxNodesAfterCursor(actualSyntaxNodes, sourceCursor)
        for(potentialNodes in finishedPotentialSyntaxNodes) {
            cutSyntaxNodesAfterCursor(potentialNodes, sourceCursor)
        }
        for(potentialNodes in buildingPotentialSyntaxNodes.values) {
            cutSyntaxNodesAfterCursor(potentialNodes, sourceCursor)
        }
    }
    private fun <TNode> cutSyntaxNodesAfterCursor(nodes: MutableList<RangedSyntaxNode<TNode>>, sourceCursor: Int) {
        var nodeIndex = nodes.binarySearch { -sourceCursor.compareTo(it.cursorRange) }
        if(nodeIndex >= 0) {
            val node = nodes[nodeIndex]
            nodes[nodeIndex] = RangedSyntaxNode(StringRange(node.cursorRange.start, min(node.cursorRange.end, sourceCursor)), node.syntaxNode)
            nodeIndex++ // All following nodes will be removed, but this one should be kept
        } else {
            nodeIndex = -nodeIndex - 1
        }
        nodes.subList(nodeIndex, nodes.size).clear()
    }

    fun copyInput(): AnalyzingResult {
        val result = AnalyzingResult(
            mappingInfo.copy(),
            filePosition,
        )
        result.documentation = documentation
        return result
    }
    fun copy() = copyInput().also {
        it.combineWith(this)
    }

    fun copyActual() = copyInput().also {
        it.combineWithActual(this)
    }

    fun filterDisabledFeatures(featureConfig: FeatureConfig, analyzerNameInserts: List<String>): AnalyzingResult {
        return AnalyzingResult(
            mappingInfo,
            if(featureConfig.isEnabled(analyzerNameInserts.map(::getSemanticTokensFeatureKey), true))
                semanticTokens
            else SemanticTokensBuilder(mappingInfo),
            if(featureConfig.isEnabled(analyzerNameInserts.map(::getDiagnosticsFeatureKey), true))
                diagnostics
            else mutableListOf(),
            if(featureConfig.isEnabled(analyzerNameInserts.map(::getColorFeatureKey), true))
                colorInfos
            else mutableListOf(),
            filePosition,
            documentation,
            getActualNodeCompressed(
                FeatureFilteredActualSyntaxNode(this, featureConfig, analyzerNameInserts)
            ),
            getPotentialNodeCompressed(
                FeatureFilteredPotentialSyntaxNode(this, featureConfig, analyzerNameInserts)
            ),
            mutableMapOf(),
        )
    }

    fun withStringEscaperActual(escaper: StringRangeTree.StringEscaper): AnalyzingResult {
        // Doesn't have to do anything at the moment
        return this
    }

    fun withStringEscaperPotential(escaper: StringRangeTree.StringEscaper): AnalyzingResult {
        return AnalyzingResult(
            mappingInfo,
            semanticTokens,
            diagnostics,
            colorInfos,
            filePosition,
            documentation,
            actualSyntaxNodes,
            getPotentialNodeCompressed(object : PotentialSyntaxNode {
                override fun getCompletions(
                    cursor: Int,
                    context: CompletionContext?,
                ): CompletableFuture<List<CompletionItem>>? =
                    this@AnalyzingResult.getCompletions(cursor, context)?.thenApply { completions ->
                        for(completion in completions) {
                            completion.textEdit.map({ textEdit ->
                                textEdit.newText = escaper.escape(textEdit.newText)
                            }, { insertReplaceEdit ->
                                insertReplaceEdit.newText = escaper.escape(insertReplaceEdit.newText)
                            })
                        }
                        completions
                    }
            }),
            mutableMapOf()
        )
    }

    private fun getActualNodeCompressed(node: ActualSyntaxNode, offset: Int = 0): MutableList<RangedSyntaxNode<ActualSyntaxNode>> {
        val actualEncompassingRange = encompassingNodeRange(actualSyntaxNodes) ?: return mutableListOf()
        return mutableListOf(RangedSyntaxNode(actualEncompassingRange + offset, node))
    }

    private fun getPotentialNodeCompressed(node: PotentialSyntaxNode, offset: Int = 0): MutableList<MutableList<RangedSyntaxNode<PotentialSyntaxNode>>> {
        val potentialEncompassingRange = (finishedPotentialSyntaxNodes.asSequence() + buildingPotentialSyntaxNodes.values.asSequence())
            .mapNotNull { encompassingNodeRange(it) }
            .reduceOrNull(StringRange::encompassing)
            ?: return mutableListOf()
        return mutableListOf(mutableListOf(RangedSyntaxNode(potentialEncompassingRange + offset, node)))
    }

    private fun encompassingNodeRange(nodes: List<RangedSyntaxNode<*>>): StringRange? {
        if(nodes.isEmpty()) return null
        return StringRange(
            nodes.first().cursorRange.start,
            nodes.last().cursorRange.end
        )
    }

    private fun getOrPutPotentialSyntaxNodesForChannel(channel: String) =
        buildingPotentialSyntaxNodes.getOrPut(channel, ::mutableListOf)

    private fun <TNode> addSyntaxNodes(dest: MutableList<RangedSyntaxNode<TNode>>, source: List<RangedSyntaxNode<TNode>>) {
        if(source.isEmpty()) return
        addSyntaxNode(dest, source.first())
        dest.addAll(source.subList(1, source.size))
    }

    private fun <TNode> addSyntaxNode(dest: MutableList<RangedSyntaxNode<TNode>>, node: RangedSyntaxNode<TNode>) {
        if(dest.isNotEmpty()) {
            val last = dest.last()
            if(last.cursorRange.end > node.cursorRange.start) {
                throw IllegalArgumentException("Syntax nodes must be added in order and not overlap")
            }
        }
        dest.add(node)
    }

    fun addActualSyntaxNode(stringRange: StringRange, node: ActualSyntaxNode) {
        addSyntaxNode(actualSyntaxNodes, RangedSyntaxNode(stringRange, node))
    }

    fun addPotentialSyntaxNode(channel: String, stringRange: StringRange, node: PotentialSyntaxNode) {
        addSyntaxNode(getOrPutPotentialSyntaxNodesForChannel(channel), RangedSyntaxNode(stringRange, node))
    }

    fun addMappedActualSyntaxNode(unmappedRange: StringRange, node: ActualSyntaxNode) {
        val startCursor = unmappedRange.start + mappingInfo.readSkippingChars

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

        var remainingLength = unmappedRange.length
        while(mappingIndex < cursorMapper.targetCursors.size) {
            val remainingLengthCoveredByMapping =
                if(mappingIndex >= 0 && mappingRelativeCursor < cursorMapper.lengths[mappingIndex])
                    min(remainingLength, cursorMapper.lengths[mappingIndex] - mappingRelativeCursor)
                else
                    remainingLength
            val mappingAbsoluteStart =
                if(mappingIndex >= 0) cursorMapper.sourceCursors[mappingIndex] + mappingRelativeCursor
                else mappingRelativeCursor
            val mappedStartPosition = startCursor + unmappedRange.length - remainingLength
            addActualSyntaxNode(
                StringRange(mappingAbsoluteStart, mappingAbsoluteStart + remainingLengthCoveredByMapping),
                node.offsetActualInput(mappedStartPosition - mappingAbsoluteStart)
            )

            if(remainingLengthCoveredByMapping >= remainingLength)
                break

            remainingLength -= remainingLengthCoveredByMapping
            mappingRelativeCursor = 0
            mappingIndex++
        }
    }

    fun toFileRange(stringRange: StringRange): Range {
        val startCursor = stringRange.start + mappingInfo.readSkippingChars
        val endCursor = stringRange.end + mappingInfo.readSkippingChars
        return Range(
            getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(startCursor), mappingInfo),
            getPositionFromCursor(mappingInfo.cursorMapper.mapToSource(endCursor), mappingInfo)
        )
    }

    fun <TNode> getSyntaxNodeAtCursor(cursor: Int, nodes: List<RangedSyntaxNode<TNode>>, inclusiveRangeEnd: Boolean): TNode? {
        val index = nodes.binarySearch {
            if(cursor < it.cursorRange.start) 1
            else if(cursor > it.cursorRange.end || (!inclusiveRangeEnd && cursor == it.cursorRange.end)) -1
            else 0
        }
        return if(index >= 0) {
            if(inclusiveRangeEnd && index + 1 < nodes.size && cursor == nodes[index + 1].cursorRange.start) {
                nodes[index + 1].syntaxNode
            } else nodes[index].syntaxNode
        } else null
    }

    override fun getDefinition(cursor: Int): CompletableFuture<Either<List<Location>, List<LocationLink>>>? =
        getSyntaxNodeAtCursor(cursor, actualSyntaxNodes, false)?.getDefinition(cursor)

    override fun getHover(cursor: Int): CompletableFuture<Hover>? =
        getSyntaxNodeAtCursor(cursor, actualSyntaxNodes, false)?.getHover(cursor)

    override fun getCompletions(
        cursor: Int,
        context: CompletionContext?,
    ): CompletableFuture<List<CompletionItem>>? {
        val completions = (finishedPotentialSyntaxNodes + buildingPotentialSyntaxNodes.values).mapNotNull {
            getSyntaxNodeAtCursor(cursor, it, true)?.getCompletions(cursor, context)
        }
        if(completions.isEmpty())
            return null
        return CompletableFuture.allOf(*completions.toTypedArray()).thenApply { completions.flatMap { it.join() } }
    }

    companion object {
        const val LANGUAGE_COMPLETION_CHANNEL = "language"
        const val DIRECTIVE_COMPLETION_CHANNEL = "directive"

        fun getCompletionsFeatureKey(analyzerNameInsert: String) = "analyzer$analyzerNameInsert.completions"
        fun getHoversFeatureKey(analyzerNameInsert: String) = "analyzer$analyzerNameInsert.hovers"
        fun getDefinitionsFeatureKey(analyzerNameInsert: String) = "analyzer$analyzerNameInsert.definitions"
        fun getDiagnosticsFeatureKey(analyzerNameInsert: String) = "analyzer$analyzerNameInsert.diagnostics"
        fun getSemanticTokensFeatureKey(analyzerNameInsert: String) = "analyzer$analyzerNameInsert.semanticTokens"
        fun getColorFeatureKey(analyzerNameInsert: String) = "analyzer$analyzerNameInsert.color"

        fun getPositionFromCursor(cursor: Int, mappingInfo: FileMappingInfo, zeroBased: Boolean = true): Position {
            val cached = mappingInfo.positionFromCursorFIFOCache.getAndMoveToLast(cursor)
            if(cached != null) {
                if(zeroBased) return cached
                // Only zero-based position is cached, so make one-based
                return Position(cached.line + 1, cached.character + 1)
            }

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
                Position(oneBasedOffset, cursor + oneBasedOffset)
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
            if(zeroBased)
                mappingInfo.positionFromCursorFIFOCache.put(cursor, pos)
            else
                // Only cache zero-based position
                mappingInfo.positionFromCursorFIFOCache.put(cursor, Position(pos.line - 1, pos.character - 1))
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

        /**
         * Removes newlines from the given cursor range and then returns the start position and length of every remaining continuous range.
         * [startCursor] and [endCursor] must be absolute cursors.
         * The output can include ranges of length 0 (for example if the input range has a length 0 or if it ends directly after a newline)
         */
        inline fun getInlineRangesBetweenCursors(startCursor: Int, endCursor: Int, mappingInfo: FileMappingInfo, rangeConsumer: (line: Int, cursor: Int, length: Int) -> Unit) {
            val startPosition = getPositionFromCursor(startCursor, mappingInfo)
            var line = startPosition.line
            var startCharactersLeft = startPosition.character
            var rangeCharactersLeft = endCursor - startCursor
            while(mappingInfo.lines.size > line && rangeCharactersLeft >= 0) {
                val lineLength = mappingInfo.lines[line].length
                if(lineLength >= rangeCharactersLeft + startCharactersLeft) {
                    rangeConsumer(line, startCharactersLeft, rangeCharactersLeft)
                    return
                }
                val consumedLength = lineLength - startCharactersLeft
                rangeConsumer(line, startCharactersLeft, consumedLength)
                line++
                rangeCharactersLeft -= consumedLength + 1 // One more because of newline character
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

    class RangedSyntaxNode<out TNode>(
        val cursorRange: StringRange,
        @JsonIgnore // Prevents some self references, because the callbacks could refer back to AnalyzingResult, and it probably just contains some cryptic stuff anyway
        val syntaxNode: TNode
    ) {
        init {
            if(cursorRange.start > cursorRange.end) {
                throw IllegalArgumentException("Start cursor must not be greater than end cursor")
            }
        }
    }

    class FeatureFilteredActualSyntaxNode(private val delegate: ActualSyntaxNode, private val featureConfig: FeatureConfig, private val analyzerNameInserts: List<String>) : ActualSyntaxNode {
        override fun getDefinition(cursor: Int) =
            if(featureConfig.isEnabled(analyzerNameInserts.map(::getDefinitionsFeatureKey), true))
                delegate.getDefinition(cursor)
            else MinecraftLanguageServer.emptyDefinitionDefault
        override fun getHover(cursor: Int) =
            if(featureConfig.isEnabled(analyzerNameInserts.map(::getHoversFeatureKey), true))
                delegate.getHover(cursor)
            else MinecraftLanguageServer.emptyHoverDefault
    }

    class FeatureFilteredPotentialSyntaxNode(private val delegate: PotentialSyntaxNode, private val featureConfig: FeatureConfig, private val analyzerNameInserts: List<String>) : PotentialSyntaxNode {
        override fun getCompletions(cursor: Int, context: CompletionContext?): CompletableFuture<List<CompletionItem>>? =
            if(featureConfig.isEnabled(analyzerNameInserts.map(::getCompletionsFeatureKey), true))
                delegate.getCompletions(cursor, context)
            else CompletableFuture.completedFuture(emptyList())
    }
}