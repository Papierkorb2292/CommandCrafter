package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.serialization.DynamicOps
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

interface SchemaOperations<TNode : Any> {
    val root: TNode
    val ops: DynamicOps<TNode>
    val placeholderNodes: Set<TNode>
    val reader: DirectiveStringReader<AnalyzingResourceCreator>
    val branchBehaviorProvider: BranchBehaviorProvider<TNode>
    val typeHints: Map<TNode, StringRangeTree.NodeTypeHint>
    fun getParentLinks(ops: DynamicOps<TNode>) : ParentLinks
}