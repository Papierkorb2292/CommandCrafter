package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.serialization.Dynamic

interface ParentLinks {
    /**
     * Returns the parent of this node in the tree. The return value is a [Dynamic], because
     * the parent is allowed to be a different type (for example for the parent of a flattened nbt compound (TODO))
     */
    fun getParent(node: Any): Dynamic<*>? = null
}

fun ParentLinks.withFallback(fallback: ParentLinks): ParentLinks = object : ParentLinks {
    override fun getParent(node: Any): Dynamic<*>? {
        val parent = this@withFallback.getParent(node)
        if(parent != null)
            return parent
        return fallback.getParent(node)
    }
}