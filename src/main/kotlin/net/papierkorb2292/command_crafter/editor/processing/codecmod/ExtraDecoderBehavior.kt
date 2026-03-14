package net.papierkorb2292.command_crafter.editor.processing.codecmod

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree.StringContent
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import org.eclipse.lsp4j.CompletionItem
import java.util.stream.Stream

interface ExtraDecoderBehavior<in TNode : Any> {
    companion object {
        private val CURRENT_EXTRA_DECODER_BEHAVIOR = ThreadLocal<RegisteredBehavior<*>>()
        val IDENTITY_LATE_ADDITION_RUNNER = object : LateAdditionRunner {
            override fun <T> acceptLateAddition(adder: () -> T): T = adder()
        }

        fun <TNode : Any> getCurrentBehavior(ops: DynamicOps<TNode>): ExtraDecoderBehavior<TNode>? {
            val behavior = CURRENT_EXTRA_DECODER_BEHAVIOR.getOrNull() ?: return null
            if(behavior.ops != ops)
                return null
            @Suppress("UNCHECKED_CAST") // We checked
            return behavior.callback as ExtraDecoderBehavior<TNode>
        }

        fun <TResult, TNode : Any> decodeWithBehavior(
            decoder: Decoder<TResult>,
            ops: DynamicOps<TNode>,
            input: TNode,
            extraDecoderBehavior: ExtraDecoderBehavior<TNode>,
        ): DataResult<Pair<TResult, TNode>> =
            CURRENT_EXTRA_DECODER_BEHAVIOR.runWithValueSwap(RegisteredBehavior(extraDecoderBehavior, ops)) {
                decoder.decode(ops, input)
            }

        fun <TResult, TNode : Any> decodeWithBehavior(
            decoder: MapDecoder<TResult>,
            ops: DynamicOps<TNode>,
            input: MapLike<TNode>,
            extraDecoderBehavior: ExtraDecoderBehavior<TNode>,
        ): DataResult<TResult> =
            CURRENT_EXTRA_DECODER_BEHAVIOR.runWithValueSwap(RegisteredBehavior(extraDecoderBehavior, ops)) {
                decoder.decode(ops, input)
            }
    }

    val branchBehavior: BranchBehavior
        get() = BranchBehavior.SHORT_CIRCUIT

    fun <TResult> onError(error: DataResult.Error<TResult>, input: TNode) {}
    fun markStringParseError(input: TNode) {}
    fun <TResult> onResult(result: TResult, isPartial: Boolean, input: TNode) {}
    fun onDecodeStart(input: TNode) {}
    fun commitErrors(level: DecoderErrorLevel) {}
    fun markErrorLateAddition(): LateAdditionRunner = IDENTITY_LATE_ADDITION_RUNNER

    fun markCompletelyAccessed(input: TNode) {}

    fun notePossibleValues(input: TNode, provider: PossibleValue.Provider<TNode>, shouldSuggest: Boolean = true) {}

    val nodeAnalyzingBehavior: NodeAnalyzingBehavior<TNode>?
        get() = null

    interface NodeAnalyzingBehavior<in TNode : Any> {
        val stringContentGetter: StringRangeTree.StringContentGetter<in TNode>
        val tree: StringRangeTree<in TNode>
        fun createNodeAnalyzingResultOverlay(node: TNode): AnalyzingResult
        fun createStringAnalyzingResultOverlay(node: TNode, stringContent: StringContent): AnalyzingResult
        fun finishNodeAnalyzingResultOverlay(node: TNode, analyzingResult: AnalyzingResult?, unmappedCursor: Int = Int.MAX_VALUE, stringContent: StringContent? = null)
    }

    data class PossibleValue<out TNode>(val element: TNode, val isNumberABoolean: Boolean = false, val preferHex: Boolean = false, val completionModifier: ((CompletionItem) -> Unit)? = null) {
        fun withIsNumberABoolean() = copy(isNumberABoolean = true)
        fun withPreferHex() = copy(preferHex = true)
        fun withCompletionModifier(modifier: (CompletionItem) -> Unit) = copy(completionModifier = modifier)

        fun interface Provider<out TNode> {
            fun getValue(): Stream<out PossibleValue<TNode>>
        }
    }

    data class RegisteredBehavior<TNode : Any>(val callback: ExtraDecoderBehavior<TNode>, val ops: DynamicOps<TNode>) {}

    enum class BranchBehavior {
        /**
         * The vanilla behavior, using the first successful value
         */
        SHORT_CIRCUIT,

        /**
         * Try out all branches that should add suggestions (for example: try out both options in EitherCodec)
         */
        ALL_POSSIBLE_DECODED,

        /**
         * Try out all branches that could contribute to the encoded value. Useful for resolving nbt paths or analyzing `data merge`.
         * Different to [ALL_POSSIBLE_DECODED] in that [ALL_POSSIBLE_ENCODED] also tries out all options in a dispatch codec, if the type field is not present.
         * Additionally, map errors like missing keys are ignored.
         */
        ALL_POSSIBLE_ENCODED
    }

    enum class DecoderErrorLevel {
        ERROR,
        WARNING,
        IGNORE
    }

    interface LateAdditionRunner {
        fun <T> acceptLateAddition(adder: () -> T): T
    }
}