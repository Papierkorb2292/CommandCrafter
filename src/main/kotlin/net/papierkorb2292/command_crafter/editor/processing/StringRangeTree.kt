package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.editor.processing.helper.advanceLine
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import org.eclipse.lsp4j.CompletionItem
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream
import kotlin.math.min

/**
 * Used for storing the [StringRange]s of the nodes in a tree alongside the nodes themselves,
 * so semantic tokens and other editor language features can use them.
 */
class StringRangeTree<TNode>(
    val root: TNode,
    /**
     * The nodes in the tree in the order they appear in the input string.
     */
    val orderedNodes: List<TNode>,
    /**
     * The ranges of the nodes in the tree.
     */
    val ranges: Map<TNode, StringRange>,
    /**
     * The ranges right before the start of a node, in which the node would have also been allowed to start.
     */
    val nodeAllowedStartRanges: Map<TNode, StringRange>,
    /**
     * The ranges of keys of maps/objects/compounds in the tree
     */
    val mapKeyRanges: Map<TNode, Collection<StringRange>>,
    /**
     * The ranges between entries of a node with children. Can be used for suggesting key names or list entries.
     */
    val internalNodeRangesBetweenEntries: Map<TNode, Collection<StringRange>>,
) {

    /**
     * Flattens the list and sorts it. The lists contained in the input must already be sorted.
     */
    private fun <T> flattenSorted(list: List<List<T>>, comparator: Comparator<T>): List<T> {
        val result = mutableListOf<T>()

        fun insertAdjustLow(element: T, lowIn: Int): Int {
            // Binary search
            var low = lowIn
            var high = result.size - 1
            while (low <= high) {
                val mid = (low + high).ushr(1) // safe from overflows
                val midVal = result[mid]
                val cmp = comparator.compare(midVal, element)

                if (cmp < 0)
                    low = mid + 1
                else if (cmp > 0)
                    high = mid - 1
                else
                    //Intersecting another element
                    return low + 1
            }
            result.add(low, element)
            return low
        }

        for(sorted in list) {
            var low = 0
            for((i, element) in sorted.withIndex()) {
                if(result.isEmpty() || comparator.compare(result.last(), element) < 0) {
                    result += sorted.subList(i, sorted.size)
                    break
                }
                low = insertAdjustLow(element, low)
            }
        }

        return result
    }

    private fun getNodeRangeOrThrow(node: TNode) =
        ranges[node] ?: throw IllegalStateException("Node $node not found in ranges")

    fun generateSemanticTokens(tokenProvider: SemanticTokenProvider<TNode>, builder: SemanticTokensBuilder) {
        val collectedTokens = mutableListOf<List<AdditionalToken>>()
        for(node in orderedNodes) {
            val range = getNodeRangeOrThrow(node)
            val nodeTokens = tokenProvider.getNodeTokenInfo(node)?.let { listOf(AdditionalToken(range, it)) }
                ?: tokenProvider.getMapNameTokenInfo(node)?.let { tokenInfo ->
                    mapKeyRanges[node]?.map { AdditionalToken(it, tokenInfo) }
                }
            if(nodeTokens != null)
                collectedTokens += nodeTokens
            collectedTokens += tokenProvider.getAdditionalTokens(node).toList()
        }

        flattenSorted(
            collectedTokens,
            Comparator.comparing(AdditionalToken::range, StringRange::compareTo)
        ).forEach {
            builder.addMultiline(it.range, it.tokenInfo.type, it.tokenInfo.modifiers)
        }
    }

    fun suggestFromAnalyzingOps(analyzingDynamicOps: AnalyzingDynamicOps<TNode>, result: AnalyzingResult, languageServer: MinecraftLanguageServer, suggestionResolver: SuggestionResolver<TNode>) {
        val resolvedSuggestions = orderedNodes.map { node ->
            val nodeSuggestions = mutableListOf<kotlin.Pair<StringRange, List<ResolvedSuggestion>>>()
            analyzingDynamicOps.nodeStartSuggestions[node]?.let { suggestions ->
                val range = nodeAllowedStartRanges[node] ?: StringRange.at(getNodeRangeOrThrow(node).start)
                nodeSuggestions += range to suggestions.map { suggestion ->
                    suggestionResolver.resolveSuggestion(suggestion, SuggestionType.NODE_START, languageServer, range, result.mappingInfo)
                }
            }
            analyzingDynamicOps.mapKeySuggestions[node]?.let { suggestions ->
                val ranges = internalNodeRangesBetweenEntries[node] ?: throw IllegalArgumentException("Node $node not found in internal node ranges between entries")
                nodeSuggestions += ranges.map { range ->
                    range to suggestions.map { suggestion ->
                        suggestionResolver.resolveSuggestion(suggestion, SuggestionType.MAP_KEY, languageServer, range, result.mappingInfo)
                    }
                }
            }
            nodeSuggestions
        }

        val sorted = flattenSorted(
            resolvedSuggestions,
            Comparator.comparing(kotlin.Pair<StringRange, *>::first, StringRange::compareTo)
        )
        for((i, suggestionEntry) in sorted.withIndex()) {
            val (range, suggestions) = suggestionEntry
            val originalEndPos = AnalyzingResult.getPositionFromCursor(range.end, result.mappingInfo)
            // Extending the range to the furthest any suggestion matches the input
            val extendedEndCursor = range.end + suggestions.maxOf {
                var currentPos = originalEndPos
                var length = 0
                while(currentPos.line < result.mappingInfo.lines.size) {
                    val line = result.mappingInfo.lines[currentPos.line]
                    if(!it.inputMatcher(line[currentPos.character])) break
                    length++

                    currentPos =
                        if(currentPos.character + 1 < line.length) currentPos.advance()
                        else currentPos.advanceLine()
                }
                length
            }

            // Make sure to not overlap with the next entry
            val newEndCursor = if(i + 1 < sorted.size) {
                val nextRange = sorted[i + 1].first
                min(extendedEndCursor, nextRange.start)
            } else extendedEndCursor

            result.addCompletionProvider(AnalyzingResult.RangedDataProvider(StringRange(range.start, newEndCursor)) { offset ->
                CompletableFuture.completedFuture(suggestions.map { it.completionItemProvider(offset) })
            }, true)
        }
    }

    class AnalyzingDynamicOps<TNode>(private val delegate: DynamicOps<TNode>) : DynamicOps<TNode> {
        companion object {
            val IS_RUNNING_MAP_GET = ThreadLocal<Boolean>()
        }
        internal val nodeStartSuggestions = mutableMapOf<TNode, MutableCollection<Suggestion<TNode>>>()
        internal val mapKeySuggestions = mutableMapOf<TNode, MutableCollection<Suggestion<TNode>>>()

        fun getNodeStartSuggestions(node: TNode) =
            nodeStartSuggestions.computeIfAbsent(node) { mutableListOf() }
        fun getMapKeySuggestions(node: TNode) =
            mapKeySuggestions.computeIfAbsent(node) { mutableListOf() }

        override fun getBooleanValue(input: TNode): DataResult<Boolean> {
            getNodeStartSuggestions(input).run {
                add(Suggestion(delegate.createBoolean(true)))
                add(Suggestion(delegate.createBoolean(false)))
            }
            return delegate.getBooleanValue(input)
        }

        override fun getStream(input: TNode): DataResult<Stream<TNode>> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createList(Stream.empty())))
            return delegate.getStream(input)
        }
        override fun getByteBuffer(input: TNode): DataResult<ByteBuffer> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createByteList(ByteBuffer.allocate(0))))
            return delegate.getByteBuffer(input)
        }
        override fun getIntStream(input: TNode): DataResult<IntStream> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createIntList(IntStream.empty())))
            return delegate.getIntStream(input)
        }
        override fun getLongStream(input: TNode): DataResult<LongStream> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createLongList(LongStream.empty())))
            return delegate.getLongStream(input)
        }
        override fun getMap(input: TNode): DataResult<MapLike<TNode>> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createMap(Collections.emptyMap())))
            return delegate.getMap(input).map { delegateMap ->
                object : MapLike<TNode> {
                    override fun get(key: TNode): TNode? {
                        val value = try {
                            IS_RUNNING_MAP_GET.set(true)
                            delegateMap.get(key)
                        } finally {
                            IS_RUNNING_MAP_GET.remove()
                        }
                        if(value == null) {
                            getMapKeySuggestions(input).add(Suggestion(key))
                        }
                        return value
                    }

                    override fun get(key: String): TNode? {
                        val value = try {
                            IS_RUNNING_MAP_GET.set(true)
                            delegateMap.get(key)
                        } finally {
                            IS_RUNNING_MAP_GET.remove()
                        }
                        if(value == null) {
                            getMapKeySuggestions(input).add(Suggestion(delegate.createString(key)))
                        }
                        return value
                    }

                    override fun entries(): Stream<Pair<TNode, TNode>> {
                        return delegateMap.entries()
                    }
                }
            }
        }
        override fun getList(input: TNode): DataResult<Consumer<Consumer<TNode>>> {
            getNodeStartSuggestions(input).add(Suggestion(delegate.createList(Stream.empty())))
            return delegate.getList(input)
        }

        //For later: Saving the path for each node to request suggestion descriptions for keys
        override fun getMapValues(input: TNode): DataResult<Stream<Pair<TNode, TNode>>> = delegate.getMapValues(input)
        override fun getMapEntries(input: TNode): DataResult<Consumer<BiConsumer<TNode, TNode>>> =
            delegate.getMapEntries(input)

        //Just delegates
        override fun empty(): TNode = delegate.empty()
        override fun emptyMap(): TNode = delegate.emptyMap()
        override fun emptyList(): TNode = delegate.emptyList()
        override fun <U> convertTo(outputOps: DynamicOps<U>, input: TNode): U = delegate.convertTo(outputOps, input)
        override fun getNumberValue(input: TNode): DataResult<Number> = delegate.getNumberValue(input)
        override fun createNumeric(number: Number): TNode = delegate.createNumeric(number)
        override fun createByte(b: Byte): TNode = delegate.createByte(b)
        override fun createShort(s: Short): TNode = delegate.createShort(s)
        override fun createInt(i: Int): TNode = delegate.createInt(i)
        override fun createLong(l: Long): TNode = delegate.createLong(l)
        override fun createFloat(f: Float): TNode = delegate.createFloat(f)
        override fun createDouble(d: Double): TNode = delegate.createDouble(d)
        override fun createBoolean(bl: Boolean): TNode = delegate.createBoolean(bl)
        override fun createString(string: String): TNode = delegate.createString(string)
        override fun mergeToList(list: TNode, value: TNode): DataResult<TNode> = delegate.mergeToList(list, value)
        override fun mergeToList(list: TNode, values: List<TNode>): DataResult<TNode> =
            delegate.mergeToList(list, values)
        override fun mergeToMap(map: TNode, key: TNode, value: TNode): DataResult<TNode> =
            delegate.mergeToMap(map, key, value)
        override fun mergeToMap(map: TNode, values: MapLike<TNode>): DataResult<TNode> =
            delegate.mergeToMap(map, values)
        override fun mergeToMap(`object`: TNode, map: Map<TNode, TNode>): DataResult<TNode> =
            delegate.mergeToMap(`object`, map)
        override fun mergeToPrimitive(`object`: TNode, object2: TNode): DataResult<TNode> =
            delegate.mergeToPrimitive(`object`, object2)
        override fun createMap(map: Map<TNode, TNode>): TNode = delegate.createMap(map)
        override fun createMap(map: Stream<Pair<TNode, TNode>>): TNode = delegate.createMap(map)
        override fun createList(stream: Stream<TNode>): TNode = delegate.createList(stream)
        override fun createByteList(buf: ByteBuffer): TNode = delegate.createByteList(buf)
        override fun createIntList(stream: IntStream): TNode = delegate.createIntList(stream)
        override fun createLongList(stream: LongStream): TNode = delegate.createLongList(stream)
        override fun remove(input: TNode, key: String): TNode = delegate.remove(input, key)
        override fun compressMaps(): Boolean = delegate.compressMaps()
        override fun listBuilder(): ListBuilder<TNode> = ListBuilder.Builder(this)
        override fun mapBuilder(): RecordBuilder<TNode> = RecordBuilder.MapBuilder(this)
        override fun getStringValue(input: TNode): DataResult<String> = delegate.getStringValue(input)
    }

    data class Suggestion<TNode>(val element: TNode)
    class ResolvedSuggestion(val inputMatcher: (Char) -> Boolean, val completionItemProvider: (Int) -> CompletionItem)
    data class TokenInfo(val type: TokenType, val modifiers: Int)
    data class AdditionalToken(val range: StringRange, val tokenInfo: TokenInfo)

    class SimpleCompletionItemProvider(
        private val text: String,
        private val suggestionRange: StringRange,
        private val mappingInfo: FileMappingInfo,
        private val label: String = text
    ) : (Int) -> CompletionItem {
        override fun invoke(offset: Int): CompletionItem {
            //Important when implementing InsertReplace
            /*val pos = AnalyzingResult.getPositionFromCursor(
                mappingInfo.cursorMapper.mapToSource(suggestionRange.start + offset),
                mappingInfo
            )*/
            return CompletionItem().also {
                it.label = label
                it.insertText = text
            }
        }
    }

    class SimpleInputMatcher(private val text: String) : (Char) -> Boolean {
        private var index = 0
        override fun invoke(char: Char): Boolean {
            return index < text.length && char == text[index++]
        }
    }

    interface SemanticTokenProvider<TNode> {
        fun getMapNameTokenInfo(map: TNode): TokenInfo?
        fun getNodeTokenInfo(node: TNode): TokenInfo?
        fun getAdditionalTokens(node: TNode): Collection<AdditionalToken>
    }

    interface SuggestionResolver<TNode> {
        fun resolveSuggestion(suggestion: Suggestion<TNode>, suggestionType: SuggestionType, languageServer: MinecraftLanguageServer, suggestionRange: StringRange, mappingInfo: FileMappingInfo): ResolvedSuggestion
    }

    enum class SuggestionType {
        NODE_START,
        MAP_KEY
    }

    class Builder<TNode> {
        private val nodesSet = Collections.newSetFromMap(IdentityHashMap<TNode, Boolean>())
        private val orderedNodes = mutableListOf<TNode>()
        private val nodeRanges = IdentityHashMap<TNode, StringRange>()
        private val nodeAllowedStartRanges = IdentityHashMap<TNode, StringRange>()
        private val mapKeyRanges = IdentityHashMap<TNode, MutableCollection<StringRange>>()
        private val internalNodeRangesBetweenEntries = IdentityHashMap<TNode, MutableCollection<StringRange>>()

        /**
         * Only adds a node into the node ordering, but doesn't add a string range for it.
         * If [build] is called before [addNode] is called for the given node, a [NodeWithoutRangeError] is thrown.
         */
        fun addNodeOrder(node: TNode) {
            if(node in nodesSet) return
            nodesSet.add(node)
            orderedNodes.add(node)
        }

        fun addNode(node: TNode, range: StringRange, nodeAllowedStart: Int? = null) {
            nodeRanges[node] = range
            if(nodeAllowedStart != null) {
                if(nodeAllowedStart > range.start)
                    throw IllegalArgumentException("Node allowed start must not be after the node start")
                nodeAllowedStartRanges[node] = StringRange(nodeAllowedStart, range.start)
            }
            if(node in nodesSet) return
            nodesSet.add(node)
            orderedNodes.add(node)
        }

        fun addRangeBetweenInternalNodeEntries(node: TNode, range: StringRange) {
            internalNodeRangesBetweenEntries.computeIfAbsent(node) { mutableListOf() }.add(range)
        }

        fun addMapKeyRange(node: TNode, range: StringRange) {
            mapKeyRanges.computeIfAbsent(node) { mutableListOf() }.add(range)
        }

        fun build(root: TNode): StringRangeTree<TNode> {
            for(node in orderedNodes) {
                if(node !in nodeRanges) {
                    throw NodeWithoutRangeError("No range specified for node $node")
                }
            }
            return StringRangeTree(root, orderedNodes, nodeRanges, nodeAllowedStartRanges, mapKeyRanges, internalNodeRangesBetweenEntries)
        }

        class NodeWithoutRangeError(message: String) : Error(message)
    }
}