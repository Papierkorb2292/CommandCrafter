package net.papierkorb2292.command_crafter.helper

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.spongepowered.asm.mixin.MixinEnvironment
import java.lang.invoke.MethodHandles

object PreLaunchMixinCoprocessorRegistration : PreLaunchEntrypoint {

    private const val MIXIN_TRANSFORMER_NAME = "org.spongepowered.asm.mixin.transformer.MixinTransformer"
    private const val PROCESSOR_FIELD_NAME = "processor"
    private const val MIXIN_PROCESSOR_NAME = "org.spongepowered.asm.mixin.transformer.MixinProcessor"
    private const val COPROCESSORS_FIELD_NAME = "coprocessors"

    private const val DECODER_OUTPUT_TRACKER_COPROCESSOR_NAME = "org.spongepowered.asm.mixin.transformer.CommandCrafterDecoderOutputTrackerMixinCoprocessor"
    private val DECODER_OUTPUT_TRACKER_COPROCESSOR_CLASS_FILE = DECODER_OUTPUT_TRACKER_COPROCESSOR_NAME.replace('.', '/') + ".class"
    private const val FALLBACK_PLAYER_SAVE_DATA_EXTRACTOR_COPROCESSOR_NAME = "org.spongepowered.asm.mixin.transformer.CommandCrafterFallbackPlayerSaveDataExtractorMixinCoprocessor"
    private val FALLBACK_PLAYER_SAVE_DATA_EXTRACTOR_COPROCESSOR_CLASS_FILE = FALLBACK_PLAYER_SAVE_DATA_EXTRACTOR_COPROCESSOR_NAME.replace('.', '/') + ".class"

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

        val lookup = MethodHandles.privateLookupIn(transformer.javaClass, MethodHandles.lookup())

        fun registerCoprocessor(classFile: String, className: String) {
            lookup.defineClass(javaClass.classLoader.getResourceAsStream(classFile)!!.readAllBytes())
            val constructor = appClassLoader.loadClass(className).getDeclaredConstructor()
            constructor.isAccessible = true
            coprocessors += constructor.newInstance()
        }

        registerCoprocessor(DECODER_OUTPUT_TRACKER_COPROCESSOR_CLASS_FILE, DECODER_OUTPUT_TRACKER_COPROCESSOR_NAME)
        registerCoprocessor(FALLBACK_PLAYER_SAVE_DATA_EXTRACTOR_COPROCESSOR_CLASS_FILE, FALLBACK_PLAYER_SAVE_DATA_EXTRACTOR_COPROCESSOR_NAME)
    }
}