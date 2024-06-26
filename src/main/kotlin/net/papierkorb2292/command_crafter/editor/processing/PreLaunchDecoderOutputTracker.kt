package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.DataResult
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.spongepowered.asm.mixin.MixinEnvironment

object PreLaunchDecoderOutputTracker : PreLaunchEntrypoint {
    private const val MIXIN_TRANSFORMER_NAME = "org.spongepowered.asm.mixin.transformer.MixinTransformer"
    private const val PROCESSOR_FIELD_NAME = "processor"
    private const val MIXIN_PROCESSOR_NAME = "org.spongepowered.asm.mixin.transformer.MixinProcessor"
    private const val COPROCESSORS_FIELD_NAME = "coprocessors"

    private const val DECODER_OUTPUT_TRACKER_COPROCESSOR_NAME = "org.spongepowered.asm.mixin.transformer.CommandCrafterDecoderOutputTrackerMixinCoprocessor"

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
        coprocessors += appClassLoader.loadClass(DECODER_OUTPUT_TRACKER_COPROCESSOR_NAME).constructors.first().newInstance()
    }

    const val ON_DECODED_NAME = "onDecoded"
    const val ON_DECODED_DESC = "(Lcom/mojang/serialization/DataResult;Ljava/lang/Object;)V"

    // Calls to this method are injected by the coprocessor at
    // every `return` statement in every Decoder.decode implementation
    @Suppress("unused")
    fun <TInput,TResult> onDecoded(dataResult: DataResult<TResult>, input: TInput) {

    }
}