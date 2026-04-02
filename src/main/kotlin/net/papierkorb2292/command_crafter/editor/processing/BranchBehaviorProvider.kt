package net.papierkorb2292.command_crafter.editor.processing

import com.google.common.base.Predicate
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior

interface BranchBehaviorProvider<in TNode> {
    companion object {
        fun getNBTMerge(): BranchBehaviorProvider<Tag> = ConditionalAllPossible(Decode, ExtraDecoderBehavior.NonCanonicalBehavior.DROP_DOWN_TO_DECODE) { it is CompoundTag }
        fun <TNode> getForPathLookup(newValue: TNode?): BranchBehaviorProvider<TNode> = ConditionalAllPossible(Decode, ExtraDecoderBehavior.NonCanonicalBehavior.IGNORE) { it != newValue }
        fun getNBTMergePathLookup(newValue: Tag): BranchBehaviorProvider<Tag> = ConditionalAllPossible(getNBTMerge(), ExtraDecoderBehavior.NonCanonicalBehavior.IGNORE) { it != newValue }

        val DEFAULT_BEHAVIOR_MODIFIER = object : BranchBehaviorModifier {
            override fun <TNode : Any> apply(provider: BranchBehaviorProvider<TNode>) = provider
        }
        val DROP_TO_DECODE_BEHAVIOR_MODIFIER = object : BranchBehaviorModifier {
            override fun <TNode : Any> apply(provider: BranchBehaviorProvider<TNode>) = Decode
        }
        val WITH_NON_CANONICAL_KEEP_BEHAVIOR_MODIFIER = object : BranchBehaviorModifier {
            override fun <TNode : Any> apply(provider: BranchBehaviorProvider<TNode>) = object : BranchBehaviorProvider<TNode> {
                override fun getBranchBehavior(includeSuggestions: Boolean): ExtraDecoderBehavior.BranchBehavior =
                    ExtraDecoderBehavior.BranchBehavior.forType(
                        provider.getBranchBehavior(includeSuggestions).type
                    ) // Always uses KEEP_BRANCH_BEHAVIOR

                override fun onDecodeStart(input: TNode) {
                    provider.onDecodeStart(input)
                }

                override fun onDecodeEnd(input: TNode) {
                    provider.onDecodeEnd(input)
                }
            }
        }
        fun modifierForProvider(newProvider: BranchBehaviorProvider<Any>) = object : BranchBehaviorModifier {
            override fun <TNode : Any> apply(provider: BranchBehaviorProvider<TNode>): BranchBehaviorProvider<TNode>
                = newProvider
        }
    }

    fun getBranchBehavior(includeSuggestions: Boolean): ExtraDecoderBehavior.BranchBehavior
    fun onDecodeStart(input: TNode)
    fun onDecodeEnd(input: TNode)

    object Decode : BranchBehaviorProvider<Any?> {
        override fun getBranchBehavior(includeSuggestions: Boolean) =
            if(includeSuggestions) ExtraDecoderBehavior.BranchBehavior.forType(ExtraDecoderBehavior.BranchBehaviorType.ALL_POSSIBLE_DECODED)
            else ExtraDecoderBehavior.BranchBehavior.forType(ExtraDecoderBehavior.BranchBehaviorType.SHORT_CIRCUIT)

        override fun onDecodeStart(input: Any?) {}
        override fun onDecodeEnd(input: Any?) {}
    }

    class ConditionalAllPossible<in TNode>(
        private val delegate: BranchBehaviorProvider<TNode>,
        private val nonCanonicalBehavior: ExtraDecoderBehavior.NonCanonicalBehavior,
        private val allPossiblePredicate: Predicate<in TNode>,
    ) : BranchBehaviorProvider<TNode> {
        private var failedPredicateCount: Int = 0
        override fun getBranchBehavior(includeSuggestions: Boolean) =
            if(failedPredicateCount != 0) delegate.getBranchBehavior(includeSuggestions)
            else ExtraDecoderBehavior.BranchBehavior.forAllPossibleEncoded(nonCanonicalBehavior)

        override fun onDecodeStart(input: TNode) {
            if(failedPredicateCount > 0 || !allPossiblePredicate.test(input)) {
                delegate.onDecodeStart(input)
                failedPredicateCount++
            }
        }

        override fun onDecodeEnd(input: TNode) {
            if(failedPredicateCount > 0) {
                delegate.onDecodeEnd(input)
                failedPredicateCount--
            }
        }
    }

    interface BranchBehaviorModifier {
        fun <TNode : Any> apply(provider: BranchBehaviorProvider<TNode>): BranchBehaviorProvider<TNode>
    }
}