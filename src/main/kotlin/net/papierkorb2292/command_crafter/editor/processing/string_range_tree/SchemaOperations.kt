package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.serialization.DynamicOps
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider

interface SchemaOperations<TNode : Any> {
    val root: TNode
    val ops: DynamicOps<TNode>
    val placeholderNodes: Set<TNode>
    val registryAccess: RegistryAccess?
    val branchBehaviorProvider: BranchBehaviorProvider<TNode>
    fun getParentLinks(ops: DynamicOps<TNode>) : ParentLinks
}