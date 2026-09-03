package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.DataResult
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior

object DecoderOutputTracker {

    const val ON_DECODE_START_NAME = "onDecodeStart"
    const val ON_DECODE_START_DESC = "(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)V"

    // Calls to this method are injected by the coprocessor at
    // the start of every Decoder.decode implementation
    fun <TInput : Any> onDecodeStart(ops: DynamicOps<TInput>, input: TInput) {
        ExtraDecoderBehavior.getCurrentBehavior(ops)?.onDecodeStart(input)
    }

    fun <TInput : Any> onDecodeStart(dynamic: Dynamic<TInput>) {
        onDecodeStart(dynamic.ops, dynamic.value)
    }

    const val ON_DECODED_NAME = "onDecoded"
    const val ON_DECODED_DESC = "(Lcom/mojang/serialization/DataResult;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)V"

    // Calls to this method are injected by the coprocessor at
    // every `return` statement in every Decoder.decode implementation
    @Suppress("unused")
    fun <TInput : Any, TResult> onDecoded(dataResult: DataResult<TResult>, ops: DynamicOps<TInput>, input: TInput) {
        val callback = ExtraDecoderBehavior.getCurrentBehavior(ops) ?: return
        dataResult.mapOrElse(
            { result -> callback.onResult(result, false, input) },
            { result ->
                callback.onError(result, input)
                result.result().ifPresent {
                    callback.onResult(it, true, input)
                }
            }
        )
    }

    fun <TInput : Any, TResult> onDecoded(dataResult: DataResult<TResult>, dynamic: Dynamic<TInput>) {
        onDecoded(dataResult, dynamic.ops, dynamic.value)
    }
}