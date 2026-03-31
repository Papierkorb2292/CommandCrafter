package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.google.common.collect.Streams
import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.MapLike
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.wrapDynamicOps
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper
import org.eclipse.lsp4j.Position
import java.nio.ByteBuffer
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream

class AnalyzingDynamicOps<TNode: Any> private constructor(
    override val delegate: DynamicOps<TNode>,
    tree: StringRangeTree<TNode>,
    internal val baseResult: AnalyzingResult,
    override val stringContentGetter: StringContent.StringContentGetter<TNode>,
    private var branchBehaviorProvider: BranchBehaviorProvider<TNode>,
    override val registries: RegistryAccess?,
) : DelegatingDynamicOps<TNode>, ExtraDecoderBehavior<TNode>, ExtraDecoderBehavior.NodeAnalyzingBehavior<TNode> {
    override var tree = tree
        private set

    override var parentLinks: ParentLinks? = null
        private set
    var accessedKeysWatcher: AccessedKeysWatcherDynamicOps<TNode>? = null
        private set

    companion object {
        private const val EMPTY_MAP_PLACEHOLDER_KEY = "command_crafter:empty_map_placeholder"

        /**
         * Wraps the given dynamic ops with AnalyzingDynamicOps.
         * @see [net.papierkorb2292.command_crafter.editor.processing.helper.wrapDynamicOps]
         */
        fun <TNode : Any> createAnalyzingOps(
            treeOperations: TreeOperations<TNode>,
            delegate: DynamicOps<TNode>,
            analyzingResult: AnalyzingResult,
        ): Pair<AnalyzingDynamicOps<TNode>, DynamicOps<TNode>> {
            val (analyzingOps, wrappedAnalyzingOps) = wrapDynamicOps(delegate) {
                if(it is AnalyzingDynamicOps && it.tree === treeOperations.stringRangeTree) it
                else AnalyzingDynamicOps(
                    it,
                    treeOperations.stringRangeTree,
                    analyzingResult,
                    treeOperations.stringGetter,
                    treeOperations.branchBehaviorProvider,
                    treeOperations.registryAccess
                )
            }
            // Apply AccessedKeysWatcher second, so it includes placeholder entries
            val (accessedKeysWatcher, wrappedAccessedKeysWatcherOps) = wrapDynamicOps(
                wrappedAnalyzingOps,
                ::AccessedKeysWatcherDynamicOps
            )
            analyzingOps.accessedKeysWatcher = accessedKeysWatcher
            analyzingOps.parentLinks = treeOperations.stringRangeTree.getParentLinks(delegate).withFallback(accessedKeysWatcher.getParentLinks(delegate))
            return analyzingOps to wrappedAccessedKeysWatcherOps
        }
    }

    internal val nodeStartSuggestions =
        IdentityHashMap<TNode, MutableCollection<ExtraDecoderBehavior.PossibleValue.Provider<TNode>>>()
    internal val mapKeySuggestions =
        IdentityHashMap<TNode, MutableCollection<ExtraDecoderBehavior.PossibleValue.Provider<TNode>>>()

    internal val nodePotentialAnalyzingResult =
        IdentityHashMap<TNode, MutableCollection<NodeAnalyzingResult>>()
    internal val nodeActualAnalyzingResult = IdentityHashMap<TNode, Pair<Int, NodeAnalyzingResult?>>()

    private var suggestEmptyString = true

    fun getNodeStartSuggestions(node: TNode) =
        nodeStartSuggestions.computeIfAbsent(node) { mutableSetOf() }

    fun getMapKeySuggestions(node: TNode) =
        mapKeySuggestions.computeIfAbsent(node) { mutableSetOf() }

    override fun createNodeAnalyzingResultOverlay(node: TNode): AnalyzingResult {
        val result = baseResult.copyInput()
        nodePotentialAnalyzingResult.getOrPut(node) { mutableListOf() } += NodeAnalyzingResult(
            result
        )
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
        nodePotentialAnalyzingResult.getOrPut(node) { mutableListOf() } += NodeAnalyzingResult(
            stringAnalyzingResult,
            stringContent.escaper
        )
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
        getNodeStartSuggestions(input).add { Stream.of(
            ExtraDecoderBehavior.PossibleValue(delegate.createByteList(
                ByteBuffer.allocate(0)))) }
        return delegate.getByteBuffer(input)
    }

    override fun getIntStream(input: TNode): DataResult<IntStream> {
        getNodeStartSuggestions(input).add { Stream.of(
            ExtraDecoderBehavior.PossibleValue(delegate.createIntList(
                IntStream.empty()))) }
        return delegate.getIntStream(input)
    }

    override fun getLongStream(input: TNode): DataResult<LongStream> {
        getNodeStartSuggestions(input).add { Stream.of(
            ExtraDecoderBehavior.PossibleValue(delegate.createLongList(
                LongStream.empty()))) }
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

                override fun entries(): Stream<com.mojang.datafixers.util.Pair<TNode, TNode>> {
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
        if(suggestEmptyString)
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
            tree.placeholderNodes + placeholder,
            tree.parentNodes
        )
        return true
    }

    private fun addMapStreamPlaceholder(map: TNode, entries: Stream<com.mojang.datafixers.util.Pair<TNode, TNode>>): Stream<com.mojang.datafixers.util.Pair<TNode, TNode>> {
        val placeholderValue = delegate.emptyList()
        val placeholderKey = delegate.createString(EMPTY_MAP_PLACEHOLDER_KEY)
        var isEmpty = true
        return Stream.concat(entries, Stream.of(
            com.mojang.datafixers.util.Pair(
                placeholderKey,
                placeholderValue
            )
        )).flatMap { pair ->
            if(pair.second !== placeholderValue) {
                isEmpty = false
                return@flatMap Stream.of(pair)
            }
            if(!isEmpty || map in placeholders)
                return@flatMap Stream.empty()
            Stream.of(pair)
        }
    }

    //For later: Saving the path for each node to request suggestion descriptions for keys
    override fun getMapValues(input: TNode): DataResult<Stream<com.mojang.datafixers.util.Pair<TNode, TNode>>> =
        delegate.getMapValues(input).map { stream ->
            addMapStreamPlaceholder(input, stream)
        }

    override fun getMapEntries(input: TNode): DataResult<Consumer<BiConsumer<TNode, TNode>>> =
        delegate.getMapEntries(input).map { consumer ->
            Consumer { biConsumer ->
                var isEmpty = true
                consumer.accept { key, value ->
                    biConsumer.accept(key, value)
                    isEmpty = false
                }
                if(isEmpty && input !in placeholders)
                    biConsumer.accept(delegate.createString(EMPTY_MAP_PLACEHOLDER_KEY), delegate.emptyList())
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

    override fun <TResult> decodeWithBehavior(
        branchBehaviorProviderOverride: BranchBehaviorProvider<TNode>?,
        convertToWarnings: Boolean,
        decodeCallback: () -> TResult
    ): TResult {
        if(branchBehaviorProviderOverride == null)
            return decodeCallback()
        val prevBehavior = this.branchBehaviorProvider
        this.branchBehaviorProvider = branchBehaviorProviderOverride
        val result = decodeCallback()
        this.branchBehaviorProvider = prevBehavior
        return result
    }

    override fun <TResult> decodeWithoutStringSuggestion(decodeCallback: () -> TResult): TResult {
        val prevSuggestEmptyString = suggestEmptyString
        suggestEmptyString = false
        val result = decodeCallback()
        suggestEmptyString = prevSuggestEmptyString
        return result
    }

    override val nodeAnalyzingBehavior: ExtraDecoderBehavior.NodeAnalyzingBehavior<TNode>
        get() = this

    override val branchBehavior: ExtraDecoderBehavior.BranchBehavior
        get() = branchBehaviorProvider.getBranchBehavior(true)

    override val decodeNonCanonical: Boolean
        get() = branchBehaviorProvider.shouldDecodeNonCanonical()

    data class NodeAnalyzingResult(val analyzingResult: AnalyzingResult, val escaper: StringEscaper? = null) {
        companion object {
            fun fromNullable(analyzingResult: AnalyzingResult?, escaper: StringEscaper?): NodeAnalyzingResult? =
                if(analyzingResult == null) null
                else NodeAnalyzingResult(analyzingResult, escaper)
        }

        fun getActual() = if(escaper == null) analyzingResult else analyzingResult.withStringEscaperActual(escaper)
        fun getPotential() = if(escaper == null) analyzingResult else analyzingResult.withStringEscaperPotential(escaper)
    }
}