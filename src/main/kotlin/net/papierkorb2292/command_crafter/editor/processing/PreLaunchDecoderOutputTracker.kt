package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.DataResult
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValue
import org.spongepowered.asm.mixin.MixinEnvironment
import java.lang.invoke.MethodHandles

object PreLaunchDecoderOutputTracker : PreLaunchEntrypoint {

    private val RESULT_CALLBACK = ThreadLocal<ResultCallback>()

    private const val MIXIN_TRANSFORMER_NAME = "org.spongepowered.asm.mixin.transformer.MixinTransformer"
    private const val PROCESSOR_FIELD_NAME = "processor"
    private const val MIXIN_PROCESSOR_NAME = "org.spongepowered.asm.mixin.transformer.MixinProcessor"
    private const val COPROCESSORS_FIELD_NAME = "coprocessors"

    private const val DECODER_OUTPUT_TRACKER_COPROCESSOR_NAME = "org.spongepowered.asm.mixin.transformer.CommandCrafterDecoderOutputTrackerMixinCoprocessor"
    private val DECODER_OUTPUT_TRACKER_COPROCESSOR_CLASS_FILE = DECODER_OUTPUT_TRACKER_COPROCESSOR_NAME.replace('.', '/') + ".class"

    override fun onPreLaunch() {
        val mixinTransformerProcessorField = Class.forName(MIXIN_TRANSFORMER_NAME).getDeclaredField(PROCESSOR_FIELD_NAME)
        mixinTransformerProcessorField.isAccessible = true
        val mixinProcessorCoprocessorsField = Class.forName(MIXIN_PROCESSOR_NAME).getDeclaredField(COPROCESSORS_FIELD_NAME)
        mixinProcessorCoprocessorsField.isAccessible = true
        val transformer = MixinEnvironment.getDefaultEnvironment().activeTransformer
        val processor = mixinTransformerProcessorField.get(transformer)
        @Suppress("UNCHECKED_CAST")
        val coprocessors = mixinProcessorCoprocessorsField.get(processor) as MutableList<in Any>

        val appClassLoader = transformer.javaClass.classLoader
        val classBytes = javaClass.classLoader.getResourceAsStream(DECODER_OUTPUT_TRACKER_COPROCESSOR_CLASS_FILE)!!.readAllBytes()
        MethodHandles.privateLookupIn(transformer.javaClass, MethodHandles.lookup()).defineClass(classBytes)
        val constructor = appClassLoader.loadClass(DECODER_OUTPUT_TRACKER_COPROCESSOR_NAME).getDeclaredConstructor()
        constructor.isAccessible = true
        coprocessors += constructor.newInstance()
    }

    fun <TResult, TInput> decodeWithCallback(decoder: Decoder<TResult>, ops: DynamicOps<TInput>, input: TInput, callback: ResultCallback): DataResult<Pair<TResult, TInput>> {
        RESULT_CALLBACK.runWithValue(callback) {
            return decoder.decode(ops, input)
        }
    }

    const val ON_DECODE_START_NAME = "onDecodeStart"
    const val ON_DECODE_START_DESC = "(Ljava/lang/Object;)V"

    // Calls to this method are injected by the coprocessor at
    // the start of every Decoder.decode implementation
    fun <TInput> onDecodeStart(input: TInput) {
        val callback = RESULT_CALLBACK.getOrNull() ?: return
        callback.onDecodeStart(input)
    }

    const val ON_DECODED_NAME = "onDecoded"
    const val ON_DECODED_DESC = "(Lcom/mojang/serialization/DataResult;Ljava/lang/Object;)V"

    // Calls to this method are injected by the coprocessor at
    // every `return` statement in every Decoder.decode implementation
    @Suppress("unused")
    fun <TInput, TResult> onDecoded(dataResult: DataResult<TResult>, input: TInput) {
        val callback = RESULT_CALLBACK.getOrNull() ?: return
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

    fun <TInput, TResult> onStringParseError(dataResult: DataResult.Error<TResult>, input: TInput, cursor: Int) {
        val callback = RESULT_CALLBACK.getOrNull() ?: return
        callback.onStringParseError(dataResult, input, cursor)
    }

    interface ResultCallback {
        fun <TInput, TResult> onError(error: DataResult.Error<TResult>, input: TInput)
        fun <TInput, TResult> onStringParseError(error: DataResult.Error<TResult>, input: TInput, cursor: Int)
        fun <TInput, TResult> onResult(result: TResult, isPartial: Boolean, input: TInput)
        fun <TInput> onDecodeStart(input: TInput)
    }
}