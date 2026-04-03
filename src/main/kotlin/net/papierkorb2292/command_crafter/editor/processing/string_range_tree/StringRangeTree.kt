package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import net.papierkorb2292.command_crafter.editor.debugger.helper.clamp
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PotentialSyntaxNode
import net.papierkorb2292.command_crafter.editor.processing.helper.compareTo
import net.papierkorb2292.command_crafter.editor.processing.helper.compareToExclusive
import net.papierkorb2292.command_crafter.helper.appendNullable
import net.papierkorb2292.command_crafter.helper.concatNullable
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableCollection
import kotlin.collections.MutableMap
import kotlin.collections.Set
import kotlin.collections.associateTo
import kotlin.collections.contains
import kotlin.collections.emptyList
import kotlin.collections.firstOrNull
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.isNotEmpty
import kotlin.collections.last
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.plusAssign
import kotlin.collections.reversed
import kotlin.collections.set
import kotlin.collections.withIndex
import kotlin.math.min

/**
 * Used for storing the [StringRange]s of the nodes in a tree alongside the nodes themselves,
 * so auto-completion and other editor language features can use them.
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
    /**
     * Maps nodes to their parent (list or map)
     */
    val parentNodes: Map<TNode, TNode>,
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
                .concatNullable(analyzingDynamicOps.accessedKeysWatcher?.accessedKeys[node]?.flatMap { mapKeyNode ->
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

    fun getNodesAndKeysSorted(accessedKeysWatcher: AccessedKeysWatcherDynamicOps<TNode>?): List<kotlin.Pair<TNode, StringRange>> {
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
            for(key in accessedKeysWatcher?.accessedKeys[node] ?: continue) {
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

    fun combineAnalyzingOpsAnalyzingResult(analyzingDynamicOps: AnalyzingDynamicOps<TNode>, stringContentGetter: StringContent.StringContentGetter<TNode>) {
        for((node, range) in getNodesAndKeysSorted(analyzingDynamicOps.accessedKeysWatcher)) {
            analyzingDynamicOps.analyzeNode(node, range) { stringContentGetter.getStringContent(node) }
        }
    }

    fun getParentLinks(ops: DynamicOps<TNode>) = object : ParentLinks {
        override fun getParent(node: Any): Dynamic<*>? {
            val parent = parentNodes[node] ?: return null
            return Dynamic(ops, parent)
        }
    }

    fun getNodeOrKeyRange(node: TNode, accessedKeys: AccessedKeysWatcherDynamicOps<TNode>): StringRange? {
        val nodeRange = ranges[node]
        if(nodeRange != null)
            return nodeRange
        val map = accessedKeys.keyToMap[node]
        if(map != null) {
            val keyRange = mapKeyRanges[map]?.firstOrNull { it.first == node }?.second
            if(keyRange != null)
                return keyRange
        }
        return null
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

    class Builder<TNode: Any> {
        private val nodesSet = Collections.newSetFromMap(IdentityHashMap<TNode, Boolean>())
        private val orderedNodes = mutableListOf<TNode?>()
        private val nodeRanges = IdentityHashMap<TNode, StringRange>()
        private val nodeAllowedStartRanges = IdentityHashMap<TNode, StringRange>()
        private val mapKeyRanges = IdentityHashMap<TNode, MutableCollection<kotlin.Pair<TNode, StringRange>>>()
        private val internalNodeRangesBetweenEntries = IdentityHashMap<TNode, MutableCollection<StringRange>>()
        private val placeholderNodes = mutableSetOf<TNode>()
        private val parentNodes = IdentityHashMap<TNode, TNode>()

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

        fun addChild(parent: TNode, child: TNode) {
            parentNodes[child] = parent
        }

        fun build(root: TNode): StringRangeTree<TNode> {
            for(node in orderedNodes) {
                if(node !in nodeRanges) {
                    throw NodeWithoutRangeError("No range specified for node $node")
                }
            }
            return StringRangeTree(root, orderedNodes.mapIndexed { index, it ->
                it ?: throw UnresolvedPlaceholderError("Node order placeholder not resolved at order index $index")
            }, nodeRanges, nodeAllowedStartRanges, mapKeyRanges, internalNodeRangesBetweenEntries, placeholderNodes, parentNodes)
        }

        class NodeWithoutRangeError(message: String) : Error(message)
        class UnresolvedPlaceholderError(message: String): Error(message)
        class NodeWithoutParentError(message: String) : Error(message)
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
            var children: MutableCollection<TNode>? = null,
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
                children = other.children.appendNullable(children)
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

            fun addChild(child: TNode) {
                val children = children
                if(children == null)
                    this.children = mutableListOf(child)
                else
                    children += child
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
                for(child in node.children ?: emptyList()) {
                    basicBuilder.addChild(node.node!!, child)
                }
            }
        }
    }
}