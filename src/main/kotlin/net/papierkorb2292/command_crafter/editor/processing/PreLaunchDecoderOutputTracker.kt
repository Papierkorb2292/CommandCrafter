package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.DataResult
import com.mojang.serialization.Dynamic
import com.mojang.serialization.DynamicOps
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import org.spongepowered.asm.mixin.MixinEnvironment
import java.lang.invoke.MethodHandles

object PreLaunchDecoderOutputTracker : PreLaunchEntrypoint {

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

    const val ON_DECODE_START_NAME = "onDecodeStart"
    const val ON_DECODE_START_DESC = "(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)V"

    // Calls to this method are injected by the coprocessor at
    // the start of every Decoder.decode implementation
    fun <TInput : Any> onDecodeStart(ops: DynamicOps<TInput>, input: TInput) {
        ExtraDecoderBehavior.getCurrentBehavior(ops)?.onDecodeStart(input)
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