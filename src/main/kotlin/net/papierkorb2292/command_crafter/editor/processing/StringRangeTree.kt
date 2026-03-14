package net.papierkorb2292.command_crafter.editor.processing

import com.google.common.collect.Streams
import com.google.gson.JsonElement
import com.mojang.brigadier.context.StringRange
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.papierkorb2292.command_crafter.editor.debugger.helper.clamp
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.helper.appendNullable
import net.papierkorb2292.command_crafter.helper.concatNullable
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.nio.ByteBuffer
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.Set
import kotlin.collections.associateTo
import kotlin.collections.contains
import kotlin.collections.emptyList
import kotlin.collections.firstNotNullOfOrNull
import kotlin.collections.firstOrNull
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.getOrPut
import kotlin.collections.isNotEmpty
import kotlin.collections.last
import kotlin.collections.lastIndex
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.mapNotNull
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.plus
import kotlin.collections.plusAssign
import kotlin.collections.reversed
import kotlin.collections.set
import kotlin.collections.sumOf
import kotlin.collections.toMutableList
import kotlin.collections.withIndex
import kotlin.math.min

/**
 * Used for storing the [StringRange]s of the nodes in a tree alongside the nodes themselves,
 * so semantic tokens and other editor language features can use them.
 */
class StringRangeTree<TNode: Any>(
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
     * The ranges of keys of maps/objects/compounds in the tree. The collection entries are a pair of a TNode representing the key and the range of the key.
     */
    val mapKeyRanges: Map<TNode, Collection<kotlin.Pair<TNode, StringRange>>>,
    /**
     * The ranges between entries of a node with children. Can be used for suggesting key names or list entries.
     */
    val internalNodeRangesBetweenEntries: Map<TNode, Collection<StringRange>>,
    /**
     * A set of all nodes that were inserted into the tree because a value could not be read,
     * but a representing node is needed for further processing.
     */
    val placeholderNodes: Set<TNode>,
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

    fun suggestFromAnalyzingOps(analyzingDynamicOps: AnalyzingDynamicOps<TNode>, result: AnalyzingResult, suggestionResolver: SuggestionResolver<TNode>) {
        val copiedMappingInfo = result.mappingInfo.copy()
        val resolvedSuggestions = orderedNodes.map { node ->
            val nodeSuggestions = mutableListOf<kotlin.Pair<StringRange, ResolvedSuggestion>>()
            analyzingDynamicOps.nodeStartSuggestions[node]?.let { suggestionProviders ->
                val range = nodeAllowedStartRanges[node] ?: StringRange.at(getNodeRangeOrThrow(node).start)
                nodeSuggestions += range to suggestionResolver.resolveNodeSuggestion(suggestionProviders, this, node, range, copiedMappingInfo)
            }
            analyzingDynamicOps.mapKeySuggestions[node]
                .concatNullable(analyzingDynamicOps.mapKeyNodes[node]?.flatMap { mapKeyNode ->
                    analyzingDynamicOps.nodeStartSuggestions[mapKeyNode] ?: emptyList()
                })?.let { suggestionProviders ->
                    val ranges = internalNodeRangesBetweenEntries[node] ?: throw IllegalArgumentException("Node $node not found in internal node ranges between entries")
                    nodeSuggestions += ranges.map { range ->
                        range to suggestionResolver.resolveMapKeySuggestion(suggestionProviders, this, node, range, copiedMappingInfo)
                    }
                }
            nodeSuggestions
        }

        val sorted = flattenSorted(
            resolvedSuggestions,
            Comparator.comparing(kotlin.Pair<StringRange, *>::first, StringRange::compareToExclusive)
        )
        for((i, suggestionEntry) in sorted.withIndex()) {
            val (range, suggestion) = suggestionEntry
            // Extending the range to the furthest any suggestion matches the input
            val extendedEndCursor = suggestion.suggestionEnd

            // Make sure to not overlap with the next entry
            val newEndCursor = if(i + 1 < sorted.size) {
                val nextRange = sorted[i + 1].first
                min(extendedEndCursor, nextRange.start - 1)
            } else extendedEndCursor

            result.addContinuouslyMappedPotentialSyntaxNode(
                AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL,
                StringRange(range.start, newEndCursor),
                suggestion.completionItemProvider
            )
        }
    }

    fun getNodesAndKeysSorted(analyzingDynamicOps: AnalyzingDynamicOps<TNode>): List<kotlin.Pair<TNode, StringRange>> {
        val result = mutableListOf<kotlin.Pair<TNode, StringRange>>()
        val pendingKeys = mutableListOf<kotlin.Pair<TNode, StringRange>>()
        for(node in orderedNodes) {
            val range = getNodeRangeOrThrow(node)
            if(pendingKeys.isNotEmpty() && pendingKeys.last().second < range) // Only check the next key, because there is assumed to be at least one node between two keys
                result += pendingKeys.removeLast()
            result += node to range
            val keys = mapKeyRanges[node] ?: continue
            // Make sure to use the key instances from the dynamic ops
            val mappedKeys: MutableMap<TNode, TNode?> = keys.associateTo(mutableMapOf()) { it.first to null }
            for(key in analyzingDynamicOps.mapKeyNodes[node] ?: continue) {
                if(key in mappedKeys)
                    mappedKeys[key] = key
            }
            for((key, range) in keys.reversed()) {
                val mappedKey = mappedKeys[key]
                if(mappedKey != null)
                    pendingKeys.add(mappedKey to range)
            }
        }

        return result
    }

    fun combineAnalyzingOpsAnalyzingResult(analyzingDynamicOps: AnalyzingDynamicOps<TNode>) {
        for((node, _) in getNodesAndKeysSorted(analyzingDynamicOps)) {
            val actualAnalyzingResult = analyzingDynamicOps.nodeActualAnalyzingResult[node]?.second?.getActual()
            if(actualAnalyzingResult != null) {
                analyzingDynamicOps.baseResult.semanticTokens.overlay(listOf(actualAnalyzingResult.semanticTokens).iterator())
                actualAnalyzingResult.semanticTokens.clear()
                analyzingDynamicOps.baseResult.combineWithActual(actualAnalyzingResult)
            }
            for(potentialNodeAnalyzingResult in analyzingDynamicOps.nodePotentialAnalyzingResult[node] ?: continue) {
                analyzingDynamicOps.baseResult.combineWithPotentialFinished(potentialNodeAnalyzingResult.getPotential())
            }
        }
    }

    data class TreeOperations<TNode: Any>(
        val stringRangeTree: StringRangeTree<TNode>,
        val ops: DynamicOps<TNode>,
        val suggestionResolver: SuggestionResolver<TNode>,
        val stringGetter: StringContentGetter<TNode>,
        val registryWrapper: HolderLookup.Provider? = null,
        val diagnosticSeverity: DiagnosticSeverity? = DiagnosticSeverity.Error,
        val branchBehaviorProvider: BranchBehaviorProvider<TNode> = BranchBehaviorProvider.Decode
    ) {
        // Size should never be 0, because a root element has to be present. If it is 1, there are no child elements.
        val isRootEmpty = stringRangeTree.orderedNodes.size == 1

        companion object {
            val IS_ANALYZING_DECODER = ThreadLocal<Boolean>()

            fun forJson(jsonTree: StringRangeTree<JsonElement>, content: String) =
                TreeOperations(
                    jsonTree,
                    JsonOps.INSTANCE,
                    StringRangeTreeJsonReader.StringRangeTreeSuggestionResolver(content),
                    StringRangeTreeJsonReader.StringContentGetter(jsonTree, content),
                )

            fun forJson(jsonTree: StringRangeTree<JsonElement>, reader: DirectiveStringReader<*>) =
                TreeOperations(
                    jsonTree,
                    JsonOps.INSTANCE,
                    StringRangeTreeJsonReader.StringRangeTreeSuggestionResolver(reader),
                    StringRangeTreeJsonReader.StringContentGetter(jsonTree, reader.string),
                )

            fun forNbt(nbtTree: StringRangeTree<Tag>, content: String) =
                TreeOperations(
                    nbtTree,
                    NbtOps.INSTANCE,
                    NbtSuggestionResolver(content),
                    NbtStringContentGetter(nbtTree, content),
                )

            fun forNbt(nbtTree: StringRangeTree<Tag>, reader: DirectiveStringReader<*>) =
                TreeOperations(
                    nbtTree,
                    NbtOps.INSTANCE,
                    NbtSuggestionResolver(reader),
                    NbtStringContentGetter(nbtTree, reader.string),
                )
        }

        fun withRegistry(wrapperLookup: HolderLookup.Provider?)
            = copy(registryWrapper = wrapperLookup)

        fun withOps(ops: DynamicOps<TNode>) = copy(ops = ops)

        fun withSuggestionResolver(resolver: SuggestionResolver<TNode>) = copy(suggestionResolver = resolver)

        fun withDiagnosticSeverity(severity: DiagnosticSeverity?) = copy(diagnosticSeverity = severity)

        fun withBranchBehaviorProvider(branchBehaviorProvider: BranchBehaviorProvider<TNode>) = copy(branchBehaviorProvider = branchBehaviorProvider)

        fun analyzeFull(analyzingResult: AnalyzingResult, contentDecoder: Decoder<*>? = null) {
            if(contentDecoder != null) {
                val (analyzingDynamicOps, wrappedOps) = AnalyzingDynamicOps.createAnalyzingOps(this, registryWrapper?.createSerializationContext(ops) ?: ops, analyzingResult)
                IS_ANALYZING_DECODER.runWithValueSwap(true) {
                    ExtraDecoderBehavior.decodeWithBehavior(
                        contentDecoder,
                        wrappedOps,
                        stringRangeTree.root,
                        FirstDecoderExtraBehavior(analyzingDynamicOps)
                    )
                }
                if(diagnosticSeverity != null)
                    generateDiagnostics(analyzingResult, contentDecoder, diagnosticSeverity)
                analyzingDynamicOps.tree.suggestFromAnalyzingOps(analyzingDynamicOps, analyzingResult, suggestionResolver)
                analyzingDynamicOps.tree.combineAnalyzingOpsAnalyzingResult(analyzingDynamicOps)
            }
        }

        fun generateDiagnostics(analyzingResult: AnalyzingResult, decoder: Decoder<*>, severity: DiagnosticSeverity = DiagnosticSeverity.Error) {
            val (accessedKeysWatcher, ops) = wrapDynamicOps(registryWrapper?.createSerializationContext(ops) ?: ops, ::AccessedKeysWatcherDynamicOps)
            val (_, filteredOps) = wrapDynamicOps(ops) { ListPlaceholderRemovingDynamicOps(stringRangeTree.placeholderNodes, it) }
            val errorCallback = stringRangeTree.LeafErrorDecoderCallback(accessedKeysWatcher, branchBehaviorProvider)
            val (_, mergeErrorSuppressingOps) = wrapDynamicOps(filteredOps, errorCallback::PathErrorSuppressingDynamicOps)
            IS_ANALYZING_DECODER.runWithValueSwap(true) {
                ExtraDecoderBehavior.decodeWithBehavior(
                    decoder,
                    mergeErrorSuppressingOps,
                    stringRangeTree.root,
                    FirstDecoderExtraBehavior(errorCallback)
                )
            }
            analyzingResult.diagnostics += errorCallback.generateDiagnostics(analyzingResult.mappingInfo, severity)
        }
    }

    class AnalyzingDynamicOps<TNode: Any> private constructor(
        override val delegate: DynamicOps<TNode>,
        tree: StringRangeTree<TNode>,
        internal val baseResult: AnalyzingResult,
        override val stringContentGetter: StringContentGetter<TNode>,
        private val branchBehaviorProvider: BranchBehaviorProvider<TNode>
    ) : DelegatingDynamicOps<TNode>, ExtraDecoderBehavior<TNode>, ExtraDecoderBehavior.NodeAnalyzingBehavior<TNode> {
        override var tree = tree
            private set

        companion object {
            private const val EMPTY_MAP_PLACEHOLDER_KEY = "command_crafter:empty_map_placeholder"

            /**
             * Wraps the given dynamic ops with AnalyzingDynamicOps.
             * @see [wrapDynamicOps]
             */
            fun <TNode : Any> createAnalyzingOps(
                treeOperations: TreeOperations<TNode>,
                delegate: DynamicOps<TNode>,
                analyzingResult: AnalyzingResult,
            ): kotlin.Pair<AnalyzingDynamicOps<TNode>, DynamicOps<TNode>> =
                wrapDynamicOps(delegate) {
                    if(it is AnalyzingDynamicOps && it.tree === treeOperations.stringRangeTree) it
                    else AnalyzingDynamicOps(it, treeOperations.stringRangeTree, analyzingResult, treeOperations.stringGetter, treeOperations.branchBehaviorProvider)
                }
        }

        internal val nodeStartSuggestions = IdentityHashMap<TNode, MutableCollection<ExtraDecoderBehavior.PossibleValue.Provider<TNode>>>()
        internal val mapKeySuggestions = IdentityHashMap<TNode, MutableCollection<ExtraDecoderBehavior.PossibleValue.Provider<TNode>>>()

        /**
         * Maps TNode instances that have keys to a collection of TNode instances that were used to represent the keys.
         *
         * For example, when MapLike.getEntries is used, which returns TNode instances of each key in the map, those instances are added to the collection,
         * such that completions that are added for those nodes can be used to suggest keys.
         */
        internal val mapKeyNodes = IdentityHashMap<TNode, MutableCollection<TNode>>()

        internal val nodePotentialAnalyzingResult = IdentityHashMap<TNode, MutableCollection<NodeAnalyzingResult>>()
        internal val nodeActualAnalyzingResult = IdentityHashMap<TNode, kotlin.Pair<Int, NodeAnalyzingResult?>>()

        fun getNodeStartSuggestions(node: TNode) =
            nodeStartSuggestions.computeIfAbsent(node) { mutableSetOf() }

        fun getMapKeySuggestions(node: TNode) =
            mapKeySuggestions.computeIfAbsent(node) { mutableSetOf() }

        fun getMapKeyNodes(node: TNode) =
            mapKeyNodes.computeIfAbsent(node) { mutableSetOf() }

        override fun createNodeAnalyzingResultOverlay(node: TNode): AnalyzingResult {
            val result = baseResult.copyInput()
            nodePotentialAnalyzingResult.getOrPut(node) { mutableListOf() } += NodeAnalyzingResult(result)
            return result
        }

        override fun createStringAnalyzingResultOverlay(node: TNode, stringContent: StringContent): AnalyzingResult {
            var absoluteContentMapper = OffsetProcessedInputCursorMapper(baseResult.mappingInfo.readSkippingChars)
                .combineWith(stringContent.cursorMapper)
            if(baseResult.mappingInfo.cursorMapper.containsTargetCursor(absoluteContentMapper.sourceCursors[0])) {
                absoluteContentMapper = baseResult.mappingInfo.cursorMapper.combineWith(absoluteContentMapper)
            } else {
                absoluteContentMapper = OffsetProcessedInputCursorMapper(baseResult.mappingInfo.skippedChars)
                    .combineWith(absoluteContentMapper)
            }
            val stringMappingInfo = FileMappingInfo(
                baseResult.mappingInfo.lines,
                absoluteContentMapper
            )
            val stringAnalyzingResult = AnalyzingResult(stringMappingInfo, Position())
            nodePotentialAnalyzingResult.getOrPut(node) { mutableListOf() } += NodeAnalyzingResult(stringAnalyzingResult, stringContent.escaper)
            return stringAnalyzingResult
        }

        override fun finishNodeAnalyzingResultOverlay(node: TNode, analyzingResult: AnalyzingResult?, unmappedCursor: Int, stringContent: StringContent?) {
            if(node !in nodeActualAnalyzingResult) {
                nodeActualAnalyzingResult[node] =
                    unmappedCursor to NodeAnalyzingResult.fromNullable(analyzingResult, stringContent?.escaper)
                return
            }
            val bestCursor = nodeActualAnalyzingResult[node]!!.first
            if(unmappedCursor > bestCursor)
                nodeActualAnalyzingResult[node] =
                    unmappedCursor to NodeAnalyzingResult.fromNullable(analyzingResult, stringContent?.escaper)
        }

        override fun getBooleanValue(input: TNode): DataResult<Boolean> {
            getNodeStartSuggestions(input).add {
                Stream.of(
                    ExtraDecoderBehavior.PossibleValue(delegate.createBoolean(true), true),
                    ExtraDecoderBehavior.PossibleValue(delegate.createBoolean(false), true)
                )
            }
            return delegate.getBooleanValue(input)
        }

        override fun getStream(input: TNode): DataResult<Stream<TNode>> {
            getNodeStartSuggestions(input).add { Stream.of(ExtraDecoderBehavior.PossibleValue(delegate.createList(Stream.empty()))) }
            return delegate.getStream(input).map { stream ->
                val placeholder = delegate.emptyList()
                var isEmpty = true
                Streams.concat(stream, Stream.of(placeholder)).flatMap {
                    if(it !== placeholder) {
                        isEmpty = false
                        return@flatMap Stream.of(it)
                    }
                    if(!isEmpty || !insertContainerPlaceholder(input, it))
                        return@flatMap Stream.empty()
                    Stream.of(it)
                }
            }
        }

        override fun getByteBuffer(input: TNode): DataResult<ByteBuffer> {
            getNodeStartSuggestions(input).add { Stream.of(ExtraDecoderBehavior.PossibleValue(delegate.createByteList(ByteBuffer.allocate(0)))) }
            return delegate.getByteBuffer(input)
        }

        override fun getIntStream(input: TNode): DataResult<IntStream> {
            getNodeStartSuggestions(input).add { Stream.of(ExtraDecoderBehavior.PossibleValue(delegate.createIntList(IntStream.empty()))) }
            return delegate.getIntStream(input)
        }

        override fun getLongStream(input: TNode): DataResult<LongStream> {
            getNodeStartSuggestions(input).add { Stream.of(ExtraDecoderBehavior.PossibleValue(delegate.createLongList(LongStream.empty()))) }
            return delegate.getLongStream(input)
        }

        override fun getMap(input: TNode): DataResult<MapLike<TNode>> {
            getNodeStartSuggestions(input).add { Stream.of(ExtraDecoderBehavior.PossibleValue(delegate.createMap(Collections.emptyMap()))) }
            return delegate.getMap(input).map { delegateMap ->
                // Map key suggestions are only added if the input is actually a map,
                // because otherwise an error can be thrown when trying to resolve the suggestions due to there being no internal ranges between entries
                val suggestedKeys = mutableSetOf<TNode>()
                getMapKeySuggestions(input).add { suggestedKeys.stream().map { ExtraDecoderBehavior.PossibleValue(it) } }
                object : MapLike<TNode> {
                    override fun get(key: TNode): TNode? {
                        suggestedKeys += key
                        return delegateMap.get(key)
                    }

                    override fun get(key: String): TNode? {
                        suggestedKeys += delegate.createString(key)
                        return delegateMap.get(key)
                    }

                    override fun entries(): Stream<Pair<TNode, TNode>> {
                        return addMapStreamPlaceholder(input, delegateMap.entries())
                    }

                    override fun toString(): String {
                        return delegateMap.toString()
                    }
                }
            }
        }

        override fun getList(input: TNode): DataResult<Consumer<Consumer<TNode>>> {
            getNodeStartSuggestions(input).add { Stream.of(ExtraDecoderBehavior.PossibleValue(delegate.createList(Stream.empty()))) }
            return delegate.getList(input).map { list ->
                Consumer { entryConsumer ->
                    var isEmpty = true
                    list.accept {
                        entryConsumer.accept(it)
                        isEmpty = false
                    }
                    if(isEmpty) {
                        val placeholder = delegate.emptyList()
                        if(insertContainerPlaceholder(input, placeholder))
                            entryConsumer.accept(placeholder)
                    }
                }
            }
        }

        override fun getStringValue(input: TNode): DataResult<String> {
            getNodeStartSuggestions(input).add { Stream.of(ExtraDecoderBehavior.PossibleValue(delegate.createString(""))) }
            return delegate.getStringValue(input)
        }

        private val placeholders = mutableSetOf<TNode>()

        private fun insertContainerPlaceholder(container: TNode, placeholder: TNode): Boolean {
            if(container in placeholders) return false

            placeholders += placeholder

            val orderedNodes = tree.orderedNodes.toMutableList()
            orderedNodes.add(orderedNodes.indexOf(container) + 1, placeholder)

            val listInnerRanges = tree.internalNodeRangesBetweenEntries[container]
                ?: throw IllegalArgumentException("Node $container not found in internal node ranges between entries")
            val listInnerRange = listInnerRanges.stream().findFirst().orElseThrow {
                IllegalArgumentException("No internal node ranges between entries found for node $container")
            }
            val ranges = IdentityHashMap(tree.ranges)
            ranges[placeholder] = StringRange.at(listInnerRange.end)
            val nodeAllowedStartRanges = IdentityHashMap(tree.nodeAllowedStartRanges)
            nodeAllowedStartRanges[placeholder] = listInnerRange
            tree = StringRangeTree(
                tree.root,
                orderedNodes,
                ranges,
                nodeAllowedStartRanges,
                tree.mapKeyRanges,
                tree.internalNodeRangesBetweenEntries,
                tree.placeholderNodes + placeholder
            )
            return true
        }

        private fun addMapStreamPlaceholder(map: TNode, entries: Stream<Pair<TNode, TNode>>): Stream<Pair<TNode, TNode>> {
            val placeholderValue = delegate.emptyList()
            val placeholderKey = delegate.createString(EMPTY_MAP_PLACEHOLDER_KEY)
            var isEmpty = true
            return Stream.concat(entries, Stream.of(Pair(placeholderKey, placeholderValue))).flatMap { pair ->
                if(pair.second !== placeholderValue) {
                    getMapKeyNodes(map).add(pair.first)
                    isEmpty = false
                    return@flatMap Stream.of(pair)
                }
                if(!isEmpty)
                    return@flatMap Stream.empty()
                insertContainerPlaceholder(map, placeholderValue)
                getMapKeyNodes(map).add(pair.first)
                Stream.of(pair)
            }
        }

        //For later: Saving the path for each node to request suggestion descriptions for keys
        override fun getMapValues(input: TNode): DataResult<Stream<Pair<TNode, TNode>>> =
            delegate.getMapValues(input).map { stream ->
                addMapStreamPlaceholder(input, stream)
            }

        override fun getMapEntries(input: TNode): DataResult<Consumer<BiConsumer<TNode, TNode>>> =
            delegate.getMapEntries(input).map { consumer ->
                Consumer { biConsumer ->
                    var isEmpty = true
                    consumer.accept { key, value ->
                        getMapKeyNodes(input).add(key)
                        biConsumer.accept(key, value)
                        isEmpty = false
                    }
                    if(isEmpty) {
                        val placeholderValue = delegate.emptyList()
                        val placeholderKey = delegate.createString(EMPTY_MAP_PLACEHOLDER_KEY)
                        if(insertContainerPlaceholder(input, placeholderValue))
                            biConsumer.accept(placeholderKey, placeholderValue)
                    }
                }
            }

        override fun onDecodeStart(input: TNode) {
            branchBehaviorProvider.onDecodeEnd(input)
        }

        override fun <TResult> onResult(result: TResult, isPartial: Boolean, input: TNode) {
            @Suppress("UNCHECKED_CAST")
            // Only use actual analyzing results for the node if it was also able to parse the full string,
            // otherwise there was an error but another decoder did not return an error, so the string
            // should not be interpreted by the errored decoder
            finishNodeAnalyzingResultOverlay(input, null)
            branchBehaviorProvider.onDecodeEnd(input)
        }

        override fun notePossibleValues(
            input: TNode,
            provider: ExtraDecoderBehavior.PossibleValue.Provider<TNode>,
            shouldSuggest: Boolean
        ) {
            if(shouldSuggest)
                getNodeStartSuggestions(input) += provider
        }

        override val nodeAnalyzingBehavior: ExtraDecoderBehavior.NodeAnalyzingBehavior<TNode>
            get() = this

        override val branchBehavior: ExtraDecoderBehavior.BranchBehavior
            get() = branchBehaviorProvider.getBranchBehavior(true)
    }

    class ResolvedSuggestion(val suggestionEnd: Int, val completionItemProvider: PotentialSyntaxNode)

    interface SuggestionResolver<TNode : Any> {
        fun resolveNodeSuggestion(
            suggestionProviders: Collection<ExtraDecoderBehavior.PossibleValue.Provider<TNode>>,
            tree: StringRangeTree<TNode>,
            node: TNode,
            suggestionRange: StringRange,
            mappingInfo: FileMappingInfo,
        ): ResolvedSuggestion

        fun resolveMapKeySuggestion(
            suggestionProviders: Collection<ExtraDecoderBehavior.PossibleValue.Provider<TNode>>,
            tree: StringRangeTree<TNode>,
            map: TNode,
            suggestionRange: StringRange,
            mappingInfo: FileMappingInfo,
        ): ResolvedSuggestion
    }

    fun interface StringEscaper {
        companion object {
            fun StringEscaper.andThen(other: StringEscaper) =
                if(other == Identity) this
                else if(this == Identity) other
                else StringEscaper { string -> other.escape(this@andThen.escape(string)) }

            fun escapeForQuotes(quotes: String) =
                StringEscaper { string -> string.replace("\\", "\\\\").replace(quotes, "\\$quotes") }
        }
        object Identity : StringEscaper {
            override fun escape(string: String) = string
        }
        fun escape(string: String): String
    }

    fun interface StringContentGetter<TNode> {
        fun getStringContent(node: TNode): StringContent?
    }

    data class StringContent(val content: String, val cursorMapper: SplitProcessedInputCursorMapper, val escaper: StringEscaper)

    data class NodeAnalyzingResult(val analyzingResult: AnalyzingResult, val escaper: StringEscaper? = null) {
        companion object {
            fun fromNullable(analyzingResult: AnalyzingResult?, escaper: StringEscaper?): NodeAnalyzingResult? =
                if(analyzingResult == null) null
                else NodeAnalyzingResult(analyzingResult, escaper)
        }

        fun getActual() = if(escaper == null) analyzingResult else analyzingResult.withStringEscaperActual(escaper)
        fun getPotential() = if(escaper == null) analyzingResult else analyzingResult.withStringEscaperPotential(escaper)
    }

    class Builder<TNode: Any> {
        private val nodesSet = Collections.newSetFromMap(IdentityHashMap<TNode, Boolean>())
        private val orderedNodes = mutableListOf<TNode?>()
        private val nodeRanges = IdentityHashMap<TNode, StringRange>()
        private val nodeAllowedStartRanges = IdentityHashMap<TNode, StringRange>()
        private val mapKeyRanges = IdentityHashMap<TNode, MutableCollection<kotlin.Pair<TNode, StringRange>>>()
        private val internalNodeRangesBetweenEntries = IdentityHashMap<TNode, MutableCollection<StringRange>>()
        private val placeholderNodes = mutableSetOf<TNode>()

        var clampNodeRange: StringRange? = null

        /**
         * Only adds a node into the node ordering, but doesn't add a string range for it.
         * If [build] is called before [addNode] is called for the given node, a [NodeWithoutRangeError] is thrown.
         */
        fun addNodeOrder(node: TNode) {
            if(node in nodesSet) return
            nodesSet.add(node)
            orderedNodes.add(node)
        }

        /**
         * Lets you delay adding the order for a node at the current position. This is useful when the
         * node instance is only available after its children have already been added.
         *
         * Note that the placeholder must be replaced by calling the returned function before [addNode] is called for the node.
         */
        fun registerNodeOrderPlaceholder(): (replacement: TNode) -> Unit {
            val index = orderedNodes.size
            orderedNodes.add(null)
            return {
                if(nodesSet.add(it))
                    orderedNodes[index] = it
            }
        }

        fun addNode(node: TNode, range: StringRange, nodeAllowedStart: Int? = null) {
            val clampedRange = clampNodeRange?.let { range.clamp(it) } ?: range
            nodeRanges[node] = clampedRange
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

        fun addMapKeyRange(node: TNode, key: TNode, range: StringRange) {
            mapKeyRanges.computeIfAbsent(node) { mutableListOf() }.add(key to range)
        }

        fun addPlaceholderNode(node: TNode) {
            placeholderNodes += node
        }

        fun build(root: TNode): StringRangeTree<TNode> {
            for(node in orderedNodes) {
                if(node !in nodeRanges) {
                    throw NodeWithoutRangeError("No range specified for node $node")
                }
            }
            return StringRangeTree(root, orderedNodes.mapIndexed { index, it ->
                it ?: throw UnresolvedPlaceholderError("Node order placeholder not resolved at order index $index")
            }, nodeRanges, nodeAllowedStartRanges, mapKeyRanges, internalNodeRangesBetweenEntries, placeholderNodes)
        }

        class NodeWithoutRangeError(message: String) : Error(message)
        class UnresolvedPlaceholderError(message: String): Error(message)
    }

    class PartialBuilder<TNode: Any> private constructor(private val stack: ArrayDeque<PartialNode<TNode>>, private var nextNodeIndex: Int, private val parentStackTop: PartialNode<TNode>?, private val poppedNodes: ArrayList<PartialNode<TNode>?>) {
        constructor(): this(ArrayDeque(), 0, null, ArrayList())

        data class PartialNode<TNode: Any>(
            val index: Int,
            var node: TNode? = null,
            var startCursor: Int? = null,
            var endCursor: Int? = null,
            var nodeAllowedStart: Int? = null,
            var rangesBetweenEntries: MutableCollection<StringRange>? = null,
            var mapKeyRanges: MutableCollection<kotlin.Pair<TNode, StringRange>>? = null,
            var isPlaceholder: Boolean? = null,
        ) {
            fun copyFrom(other: PartialNode<TNode>) {
                require(index == other.index) { "Tried to copy from a node with a different index" }
                node = other.node ?: node
                startCursor = other.startCursor ?: startCursor
                endCursor = other.endCursor ?: endCursor
                nodeAllowedStart = other.nodeAllowedStart ?: nodeAllowedStart
                rangesBetweenEntries = rangesBetweenEntries.appendNullable(other.rangesBetweenEntries)
                mapKeyRanges = mapKeyRanges.appendNullable(other.mapKeyRanges)
                isPlaceholder = other.isPlaceholder ?: isPlaceholder
            }

            fun addRangeBetweenEntries(range: StringRange) {
                val rangesBetweenEntries = rangesBetweenEntries
                if(rangesBetweenEntries == null)
                    this.rangesBetweenEntries = mutableListOf(range)
                else
                    rangesBetweenEntries.add(range)
            }

            fun addMapKeyRange(key: TNode, range: StringRange) {
                val mapKeyRanges = mapKeyRanges
                if(mapKeyRanges == null)
                    this.mapKeyRanges = mutableListOf(key to range)
                else
                    mapKeyRanges.add(key to range)
            }
        }

        fun pushNode(): PartialNode<TNode> {
            val node = PartialNode<TNode>(nextNodeIndex++)
            stack.addLast(node)
            return node
        }

        fun popNode() {
            val node = stack.removeLast()
            require(node.node != null && node.startCursor != null && node.endCursor != null) { "Tried to pop incomplete node: $node" }
            poppedNodes.ensureCapacity(node.index + 1)
            for(i in poppedNodes.size..node.index)
                poppedNodes.add(null)
            poppedNodes[node.index] = node
        }

        fun peekNode() = stack.lastOrNull() ?: parentStackTop

        fun pushBuilder(): PartialBuilder<TNode> {
            val top = peekNode()
            val branchedTop = if(top != null) PartialNode<TNode>(top.index) else null
            return PartialBuilder(ArrayDeque(), nextNodeIndex, branchedTop, poppedNodes)
        }

        fun popBuilder(other: PartialBuilder<TNode>) {
            while(other.stack.isNotEmpty())
                other.popNode()
            other.parentStackTop?.let {
                peekNode()!!.copyFrom(it)
            }
            nextNodeIndex = other.nextNodeIndex
        }

        fun addToBasicBuilder(basicBuilder: Builder<TNode>) {
            // Only consider elements up to nextNodeIndex, because elements beyond that were added by
            // pushed builders that haven't been merged and thus shouldn't be build
            for(node in poppedNodes.subList(0, nextNodeIndex)) {
                require(node != null) { "Not all required nodes have been popped" }
                basicBuilder.addNode(node.node!!, StringRange(node.startCursor!!, node.endCursor!!), node.nodeAllowedStart)
                node.rangesBetweenEntries?.forEach { basicBuilder.addRangeBetweenInternalNodeEntries(node.node!!, it) }
                node.mapKeyRanges?.forEach { basicBuilder.addMapKeyRange(node.node!!, it.first, it.second) }
                if(node.isPlaceholder == true)
                    basicBuilder.addPlaceholderNode(node.node!!)
            }
        }
    }

    /**
     * A decoder  callback that tracks all errors and the range of the input that caused them.
     *
     * This callback only keeps the errors that are caused by the leaf nodes of a StringRangeTree that is reduced
     * to only the nodes with errors. In other words, errors from a node are ignored if its children already have errors.
     */
    inner class LeafErrorDecoderCallback(
        private val accessedKeysWatcherDynamicOps: AccessedKeysWatcherDynamicOps<TNode>,
        private val branchBehaviorProvider: BranchBehaviorProvider<TNode>
    ) : ExtraDecoderBehavior<TNode> {
        private var stack = ArrayList<ErrorStackEntry<TNode>>(16)
        private val lateAdditionMergers = ArrayList<() -> Unit>()
        init {
            stack.add(ErrorStackEntry(root))
        }
        private fun getNodeRange(node: TNode): StringRange? {
            ranges[node]?.let {
                return it
            }
            // Node could be a key, so check if key access happened for it
            val map = accessedKeysWatcherDynamicOps.accessedKeys.firstNotNullOfOrNull {
                if(node in it.value) {
                    it.key
                } else null
            } ?: return null
            return mapKeyRanges[map]?.firstOrNull { it.first == node }?.second
        }

        override fun <TResult> onError(error: DataResult.Error<TResult>, input: TNode) {
            val range = getNodeRange(input) ?: return
            addError(error, range)
            popStack()
        }

        override fun markStringParseError(input: TNode) {
            val range = getNodeRange(input) ?: return

            // Don't add error message, diagnostics are already generated by the decoder.
            // Should not be replaced by other decoder errors, because string parsing errors are more specific
            addError(null, StringRange.at(range.start))
        }

        private fun addError(error: DataResult.Error<*>?, range: StringRange) {
            val stackEntry = stack.last()
            if(!stackEntry.ignoreErrors) {
                stackEntry.comittedDiagnostics.errors += range to error?.message()
                stackEntry.ignoreErrors = true
            }
        }

        override fun <TResult> onResult(result: TResult, isPartial: Boolean, input: TNode) {
            branchBehaviorProvider.onDecodeEnd(input)
            if(isPartial) return
            // Since decoding was successful, all other errors that children added are cleared.
            // Warnings are kept, since they exist even if the result was decoded correctly
            stack.last().clearChildErrors()
            popStack()
        }

        override fun onDecodeStart(input: TNode) {
            pushStack(input)
            branchBehaviorProvider.onDecodeStart(input)
        }

        override fun commitErrors(level: ExtraDecoderBehavior.DecoderErrorLevel) {
            val stackEntry = stack.last()
            when(level) {
                ExtraDecoderBehavior.DecoderErrorLevel.IGNORE -> stackEntry.isEntryIgnored = true
                ExtraDecoderBehavior.DecoderErrorLevel.WARNING -> {
                    stackEntry.childDiagnostics.values.forEach { child ->
                        stackEntry.comittedDiagnostics.warnings += child.errors
                        child.errors.clear()
                    }
                }
                ExtraDecoderBehavior.DecoderErrorLevel.ERROR -> {
                    stackEntry.childDiagnostics.values.forEach { child ->
                        stackEntry.comittedDiagnostics.errors += child.errors
                        child.errors.clear()
                    }
                }
            }
        }

        override fun markErrorLateAddition(): ExtraDecoderBehavior.LateAdditionRunner {
            val stackCopy = ArrayList(stack)
            stack.last().hasLateAddition = true
            lateAdditionMergers += {
                for(i in stackCopy.lastIndex downTo 1) {
                    val entry = stackCopy[i]
                    if(entry.isEntryIgnored)
                        break
                    stackCopy[i - 1].offerChild(entry.node, entry.getAllDiagnostics())
                }
            }
            return object : ExtraDecoderBehavior.LateAdditionRunner {
                override fun <T> acceptLateAddition(adder: () -> T): T {
                    val prevStack = stack
                    stack = ArrayList(16)
                    stack += stackCopy.last()
                    val result = adder()
                    stack = prevStack
                    return result
                }
            }
        }

        override val branchBehavior: ExtraDecoderBehavior.BranchBehavior
            get() = branchBehaviorProvider.getBranchBehavior(false)

        private fun pushStack(node: TNode) {
            stack += ErrorStackEntry(node)
        }

        private fun popStack() {
            val popped = stack.removeLast()
            if(popped.isEntryIgnored) return
            val next = stack.last()
            if(popped.hasLateAddition) {
                next.hasLateAddition = true
                return // Will be merged later when generating diagnostics
            }
            next.offerChild(popped.node, popped.getAllDiagnostics())
        }

        fun generateDiagnostics(fileMappingInfo: FileMappingInfo, severity: DiagnosticSeverity = DiagnosticSeverity.Error): List<Diagnostic> {
            for(i in lateAdditionMergers.lastIndex downTo 0)
                lateAdditionMergers[i].invoke()
            return stack.last().getAllDiagnostics().build(fileMappingInfo, severity)
        }

        inner class PathErrorSuppressingDynamicOps(override val delegate: DynamicOps<TNode>): DelegatingDynamicOps<TNode> {
            fun <TResult> onNodeAccess(input: TNode, dataResult: DataResult<TResult>): DataResult<TResult> {
                // Don't show errors for missing keys or invalid list lengths when Minecraft doesn't actually enforce it (like in a path or for a merge)
                if(dataResult.isSuccess && branchBehavior == ExtraDecoderBehavior.BranchBehavior.ALL_POSSIBLE_ENCODED && input == stack.last().node) {
                    stack.last().ignoreErrors = true
                }
                return dataResult
            }

            override fun getMap(input: TNode) = onNodeAccess(input, delegate.getMap(input))
            override fun getMapValues(input: TNode) = onNodeAccess(input, delegate.getMapValues(input))
            override fun getMapEntries(input: TNode) = onNodeAccess(input, delegate.getMapEntries(input))
            override fun getList(input: TNode) = onNodeAccess(input, delegate.getList(input))
            override fun getStream(input: TNode) = onNodeAccess(input, delegate.getStream(input))
            override fun getByteBuffer(input: TNode) = onNodeAccess(input, delegate.getByteBuffer(input))
            override fun getIntStream(input: TNode) = onNodeAccess(input, delegate.getIntStream(input))
            override fun getLongStream(input: TNode) = onNodeAccess(input, delegate.getLongStream(input))
        }
    }

    private class ErrorStackEntry<TNode>(
        val node: TNode,
        val comittedDiagnostics: NodeDiagnostics = NodeDiagnostics(),
        val childDiagnostics: MutableMap<TNode, NodeDiagnostics> = mutableMapOf(),
        var ignoreErrors: Boolean = false,
        var isEntryIgnored: Boolean = false,
        var hasLateAddition: Boolean = false,
    ) {
        fun clearChildErrors() {
            childDiagnostics.values.forEach { it.errors.clear() }
        }

        fun getAllDiagnostics(): NodeDiagnostics {
            val diagnostics = NodeDiagnostics(comittedDiagnostics.errors.toMutableList(), comittedDiagnostics.warnings.toMutableList())
            diagnostics.errors += childDiagnostics.values.flatMap { it.errors }
            diagnostics.warnings += childDiagnostics.values.flatMap { it.warnings }
            return diagnostics
        }

        fun offerChild(child: TNode, diagnostics: NodeDiagnostics) {
            val prev = childDiagnostics[child]
            if(prev == null) {
                if(diagnostics.errors.isNotEmpty()) {
                    // Errors from this entry should be ignored in favor of errors from the child
                    this.ignoreErrors = true
                    this.comittedDiagnostics.errors.clear()
                }
                childDiagnostics[child] = diagnostics
                return
            }
            // Select option with fewer errors
            if(prev.errors.size != diagnostics.errors.size) {
                if(prev.errors.size > diagnostics.errors.size)
                    childDiagnostics[child] = diagnostics
                return
            }
            // Select option with fewer warnings
            if(prev.warnings.size != diagnostics.warnings.size) {
                if(prev.warnings.size > diagnostics.warnings.size)
                    childDiagnostics[child] = diagnostics
                return
            }

            // Select option with smaller (more specific) diagnostics
            val prevDiagnosticTotalLength = prev.errors.sumOf { it.first.length } + prev.warnings.sumOf { it.first.length }
            val newDiagnosticTotalLength = diagnostics.errors.sumOf { it.first.length } + diagnostics.warnings.sumOf { it.first.length }
            if(prevDiagnosticTotalLength != newDiagnosticTotalLength) {
                if(prevDiagnosticTotalLength > newDiagnosticTotalLength)
                    childDiagnostics[child] = diagnostics
                return
            }

            // Just add diagnostics from both
            prev.errors += diagnostics.errors
            prev.warnings += diagnostics.warnings
        }
    }
    private class NodeDiagnostics(
        val errors: MutableList<kotlin.Pair<StringRange, String?>> = mutableListOf(),
        val warnings: MutableList<kotlin.Pair<StringRange, String?>> = mutableListOf()
    ) {
        fun build(fileMappingInfo: FileMappingInfo, severity: DiagnosticSeverity): List<Diagnostic> {
            fun createDiagnostic(message: String, range: StringRange, severity: DiagnosticSeverity) =
                Diagnostic().also {
                    it.range = Range(
                        AnalyzingResult.getPositionFromCursor(fileMappingInfo.cursorMapper.mapToSource(range.start + fileMappingInfo.readSkippingChars), fileMappingInfo),
                        AnalyzingResult.getPositionFromCursor(fileMappingInfo.cursorMapper.mapToSource(range.end + fileMappingInfo.readSkippingChars), fileMappingInfo)
                    )
                    it.message = message
                    it.severity = severity
                }

            return errors.mapNotNull { (range, message) ->
                if(message == null)
                    return@mapNotNull null
                createDiagnostic(message, range, severity)
            } + warnings.mapNotNull { (range, message) ->
                if(message == null)
                    return@mapNotNull null
                createDiagnostic(message, range, DiagnosticSeverity.Warning)
            }
        }
    }

}