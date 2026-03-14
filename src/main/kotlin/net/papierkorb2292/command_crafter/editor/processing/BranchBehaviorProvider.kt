package net.papierkorb2292.command_crafter.editor.processing

import com.google.common.base.Predicate
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior

interface BranchBehaviorProvider<in TNode> {
    companion object {
        val NBT_MERGE: BranchBehaviorProvider<Tag> = ConditionalAllPossible { it is CompoundTag }
        fun <TNode> getForPathLookup(newValue: TNode?): BranchBehaviorProvider<TNode> = ConditionalAllPossible { it != newValue }
    }

    fun getBranchBehavior(includeSuggestions: Boolean): ExtraDecoderBehavior.BranchBehavior
    fun onDecodeStart(input: TNode)
    fun onDecodeEnd(input: TNode)

    object Decode : BranchBehaviorProvider<Any?> {
        override fun getBranchBehavior(includeSuggestions: Boolean) =
            if(includeSuggestions) ExtraDecoderBehavior.BranchBehavior.ALL_POSSIBLE_DECODED
            else ExtraDecoderBehavior.BranchBehavior.SHORT_CIRCUIT

        override fun onDecodeStart(input: Any?) {}
        override fun onDecodeEnd(input: Any?) {}
    }

    class ConditionalAllPossible<in TNode>(private val allPossiblePredicate: Predicate<in TNode>) : BranchBehaviorProvider<TNode> {
        private var failedPredicateCount: Int = 0
        override fun getBranchBehavior(includeSuggestions: Boolean) =
            if(failedPredicateCount != 0) Decode.getBranchBehavior(includeSuggestions)
            else ExtraDecoderBehavior.BranchBehavior.ALL_POSSIBLE_ENCODED

        override fun onDecodeStart(input: TNode) {
            if(failedPredicateCount > 0 || !allPossiblePredicate.test(input)) failedPredicateCount++
        }

        override fun onDecodeEnd(input: TNode) {
            if(failedPredicateCount > 0) failedPredicateCount--
        }
    }
}