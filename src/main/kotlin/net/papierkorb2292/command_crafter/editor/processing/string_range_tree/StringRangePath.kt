package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.brigadier.context.StringRange
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import net.minecraft.nbt.*
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTree.NodeTypeHint
import net.papierkorb2292.command_crafter.mixin.editor.processing.EndTagAccessor
import java.util.*

class StringRangePath(
    val root: Tag,
    val segments: List<Segment>,
    val collisions: List<Collision>,
    val placeholderNodes: Set<Tag>,
    val parentNodes: Map<Tag, Tag>,
    val typeHints: Map<Tag, NodeTypeHint>,
) {
    fun getParentLinks(ops: DynamicOps<Tag>) = object : ParentLinks {
        override fun getParent(node: Any): Dynamic<*>? {
            val parent = parentNodes[node] ?: return null
            return Dynamic(ops, parent)
        }
    }

    class Builder {
        private val segments = mutableListOf<Segment>()
        private val collisions = mutableListOf<Collision>()
        private val placeholderNodes = Collections.newSetFromMap(IdentityHashMap<Tag, Boolean>())
        private val parentNodes = IdentityHashMap<Tag, Tag>()
        private val typeHints = IdentityHashMap<Tag, NodeTypeHint>()
        private var root: Tag = getEmptyPlaceholder(null)

        private val replacements = IdentityHashMap<Tag, Tag>()
        private var nextNode: Tag = root
        private var nextNodeConsumer: (Tag) -> Unit = { root = it }
        private var nextNodeCanHaveCompoundFilter: Boolean = true
        private var endCursor: Int = 0

        fun addKeyAccess(key: String, range: StringRange, isTrailing: Boolean) {
            if(collisions.isNotEmpty())
                return // We can't reliably merge anything more
            val compound = mergeInto(nextNode, CompoundTag(), if(isTrailing) null else ({ range }))
            nextNode = compound
            nextNodeConsumer(compound)
            val tree = getSegmentStartTree(range.start)
            segments += Segment(tree, range, key, nextNodeCanHaveCompoundFilter, isTrailing)
            nextNode = (compound as? CompoundTag)?.get(key) ?: getEmptyPlaceholder(compound)
            nextNodeConsumer = { child ->
                (compound as CompoundTag).put(key, child)
            }
            nextNodeCanHaveCompoundFilter = true //TODO: If range is empty, only allow if there's a macro at this position?
            endCursor = range.end
        }

        /**
         * Adds a compound or list segment. Lists must have exactly one element.
         */
        fun addFilter(filter: StringRangeTree<Tag>) {
            if(collisions.isNotEmpty())
                return // We can't reliably merge anything more
            val range = filter.ranges[filter.root]!!
            placeholderNodes += filter.placeholderNodes
            typeHints += filter.typeHints // Add before mergeInto
            val root = mergeInto(nextNode, filter.root) { filter.ranges[it]!! }
            if(root is ListTag) {
                nextNodeConsumer(root)
                val lastIndex = root.lastIndex
                nextNode = root[lastIndex]
                nextNodeConsumer = { child ->
                    root[lastIndex] = child
                }
            } else {
                nextNode = root
            }
            parentNodes += filter.parentNodes
            segments += Segment(filter, range, null, nextNodeCanHaveCompoundFilter, false)
            nextNodeCanHaveCompoundFilter = false
            endCursor = range.end
        }

        fun addListAccess(range: StringRange, contentEnd: Int) {
            if(collisions.isNotEmpty())
                return // We can't reliably merge anything more
            // Use list with one placeholder entry to continue the path
            val list = ListTag()
            list.add(getEmpty())
            val builder = StringRangeTree.Builder<Tag>()
            builder.addNode(list, range, range.start)
            builder.addNode(list[0], StringRange(range.start + 1, contentEnd), range.start + 1) // Skip '['
            builder.addChild(list, list[0])
            builder.addPlaceholderNode(list[0])
            addFilter(builder.build(list))
        }

        private fun getSegmentStartTree(cursor: Int): StringRangeTree<Tag> {
            val builder = StringRangeTree.Builder<Tag>()
            builder.addNode(nextNode, StringRange.at(cursor), cursor)
            return builder.build(nextNode)
        }

        // TODO: Recognize collisions when lists have different types
        private fun mergeInto(src: Tag, newTag: Tag, collisionRangeGetter: ((Tag) -> StringRange)? = null): Tag {
            fun onCollision() {
                if(collisionRangeGetter != null)
                    collisions += Collision(collisionRangeGetter(newTag), src)
            }

            if(newTag is EndTag) {
                replacements[newTag] = src
                return src
            }

            when(src) {
                is EndTag -> {
                    replacements[src] = newTag
                    return newTag
                }
                is PrimitiveTag -> {
                    // Try to use the node that is already there
                    if(newTag == src) {
                        replacements[newTag] = src
                    } else {
                        onCollision()
                    }
                    return src
                }
                is CollectionTag -> {
                    if(src !is ListTag)
                        throw IllegalArgumentException("Collection inside path must be a list")

                    val srcTypeHint = typeHints[src] // No need to apply replacements, because only the type hint for the current replacement matters
                    requireNotNull(srcTypeHint) { "srcTypeHint for list should never be null, since the only way to have an existing value is a condition, for which the SNBT parser always adds type hints"}
                    val newTypeHint = typeHints[newTag]

                    if(newTag !is CollectionTag || newTypeHint != null && newTypeHint != srcTypeHint) {
                        onCollision()
                    } else {
                        replacements[newTag] = src
                        if(srcTypeHint == NodeTypeHint.LIST) {
                            for(child in newTag)
                                src.addTag(src.size, child)
                        } else if(src.size != newTag.size()) {
                            onCollision()
                        } else {
                            // Compare all elements of an array
                            for(i in 0 until src.size) {
                                mergeInto(src[i], newTag[i], collisionRangeGetter)
                            }
                        }
                    }
                    return src
                }
                is CompoundTag -> {
                    if(newTag !is CompoundTag) {
                        onCollision()
                    } else {
                        replacements[newTag] = src
                        for((key, child) in newTag.entrySet()) {
                            src.put(key, mergeInto(src.get(key) ?: getEmpty(), child, collisionRangeGetter))
                        }
                    }
                    return src
                }
            }
        }

        private fun getEmpty(): Tag = EndTagAccessor.callInit()
        private fun getEmptyPlaceholder(parent: Tag?): Tag {
            val empty = getEmpty()
            if(parent != null)
                parentNodes[empty] = parent
            placeholderNodes += empty
            return empty
        }

        fun buildStandalone(): StringRangePath {
            if(collisions.isEmpty() && segments.lastOrNull()?.range?.isEmpty != true) { // Only add final segment if there isn't already an empty segment at the end
                val lastSegmentTree = getSegmentStartTree(endCursor)
                segments += Segment(lastSegmentTree, StringRange.at(endCursor), null, nextNodeCanHaveCompoundFilter, true)
                nextNodeConsumer(nextNode)
            }
            flattenReplacements()
            return StringRangePath(
                root,
                getSegmentsWithReplacements(),
                collisions,
                placeholderNodes, // Not mapped with replacements, because it only matters whether the last merged node is a placeholder
                parentNodes.map { (replacements[it.key] ?: it.key) to (replacements[it.value] ?: it.value) }.toMap(),
                typeHints.mapKeys { replacements[it.key] ?: it.key }
            )
        }

        private fun flattenReplacements() {
            for(key in replacements.keys) {
                flattenReplacement(key)
            }
        }

        private fun flattenReplacement(src: Tag): Tag {
            val replacement = replacements[src] ?: return src
            val flattened = flattenReplacement(replacement)
            if(replacement !== flattened) {
                replacements[src] = flattened
            }
            return flattened
        }

        private fun getSegmentsWithReplacements(): List<Segment> =
            segments.map {
                it.copy(tree = it.tree.copyWithReplacements(replacements))
            }
    }

    /**
     * One segment of the nbt path. Always has a [tree] that begins at the start of the [range]. This tree
     * is optionally followed a [key] that the path accesses afterward. If [key] is present or if the segment
     * is at the end of the path, the [tree] is not actually present in the source file. It was added by the builder
     * just to give auto-completion for all valid segment types at the start of the range.
     */
    data class Segment(val tree: StringRangeTree<Tag>, val range: StringRange, val key: String?, val allowsCompoundFilter: Boolean, val isTrailing: Boolean)
    data class Collision(val range: StringRange, val present: Tag)
}