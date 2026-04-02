package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.serialization.DataResult
import net.minecraft.core.RegistryAccess
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior

/**
 * Only calls the delegate for the start and result of the first decoder that a node is passed.
 */
class FirstDecoderExtraBehavior<TNode : Any>(val delegate: ExtraDecoderBehavior<TNode>): ExtraDecoderBehavior<TNode> {
    private val decodeStackCounts = mutableMapOf<TNode, Int>()
    private fun postDecrementDecodeStackCount(node: TNode): Int {
        val count = decodeStackCounts[node]!!
        if(count == 1)
            decodeStackCounts.remove(node)
        else
            decodeStackCounts[node] = count - 1

        return count
    }

    override fun <TResult> onError(error: DataResult.Error<TResult>, input: TNode) {
        if(postDecrementDecodeStackCount(input) > 1) return
        delegate.onError(error, input)
    }

    override fun <TResult> onResult(result: TResult, isPartial: Boolean, input: TNode) {
        if(isPartial) {
            // onError has been called before
            if(input !in decodeStackCounts)
                delegate.onResult(result, true, input)
        } else {
            if(postDecrementDecodeStackCount(input) > 1) return
            delegate.onResult(result, false, input)
        }
    }

    override fun onDecodeStart(input: TNode) {
        val newCount = decodeStackCounts.getOrDefault(input, 0) + 1
        decodeStackCounts[input] = newCount
        if(newCount > 1) return
        delegate.onDecodeStart(input)
    }

    override fun <TResult> decodeWithBehavior(branchBehaviorModifier: BranchBehaviorProvider.BranchBehaviorModifier, convertToWarnings: Boolean, decodeCallback: () -> TResult) =
        delegate.decodeWithBehavior(branchBehaviorModifier, convertToWarnings, decodeCallback)

    override fun markErrorLateAddition(): ExtraDecoderBehavior.LateAdditionRunner =
        delegate.markErrorLateAddition()

    override fun markStringParseError(input: TNode) = delegate.markStringParseError(input)
    override fun markCompletelyAccessed(input: TNode) = delegate.markCompletelyAccessed(input)
    override fun notePossibleValues(
        input: TNode,
        provider: ExtraDecoderBehavior.PossibleValue.Provider<TNode>,
        shouldSuggest: Boolean
    ) = delegate.notePossibleValues(input, provider, shouldSuggest)

    override val nodeAnalyzingBehavior: ExtraDecoderBehavior.NodeAnalyzingBehavior<TNode>?
        get() = delegate.nodeAnalyzingBehavior

    override val branchBehavior: ExtraDecoderBehavior.BranchBehavior
        get() = delegate.branchBehavior

    override val registries: RegistryAccess?
        get() = delegate.registries

    override val parentLinks: ParentLinks?
        get() = delegate.parentLinks
}