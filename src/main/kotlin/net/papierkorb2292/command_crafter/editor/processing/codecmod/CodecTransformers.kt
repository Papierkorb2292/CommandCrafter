package net.papierkorb2292.command_crafter.editor.processing.codecmod

import com.google.common.collect.BiMap
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.PrimitiveCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.locale.Language
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceKey
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.pack.PackFormat
import net.minecraft.tags.TagEntry
import net.minecraft.tags.TagKey
import net.minecraft.util.ExtraCodecs
import net.minecraft.util.InclusiveRange
import net.minecraft.util.StringRepresentable
import net.papierkorb2292.command_crafter.codecmod.CodecMod
import net.papierkorb2292.command_crafter.editor.debugger.helper.StringRangeContainer
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugHandler.Companion.TAG_PARSING_ELEMENT_RANGES
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper.SuggestionsProvider
import net.papierkorb2292.command_crafter.editor.processing.PrimitiveCodecSuggestionWrapper
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer.Companion.CURRENT_TAG_ANALYZING_REGISTRY
import net.papierkorb2292.command_crafter.editor.processing.helper.PackedEncoderColorInfo
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.mixin.editor.processing.LanguageImplAccessor
import java.util.*
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

@Suppress("unused")
object CodecTransformers {

    @JvmStatic
    @CodecMod(target = ExtraCodecs.LateBoundIdMapper::class, methodName = "codec", fieldAccess = ["idToValue"])
    fun <T, I> addIdMapperSuggestions(codec: Codec<T>, idToValue: BiMap<I, *>, idCodec: Codec<I>): Codec<T> = CodecSuggestionWrapper(codec, object : CodecSuggestionWrapper.SuggestionsProvider {
        override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> =
            idToValue.keys.stream().map { idCodec.encodeStart(ops, it).getOrThrow() }
    })

    @JvmStatic
    @CodecMod(target = ExtraCodecs::class, javaFieldWrite = "RGB_COLOR_CODEC")
    fun addRGBColorInfo(codec: Codec<Int>): Codec<Int> = PackedEncoderColorInfo.wrapCodec(codec, false)
    @JvmStatic
    @CodecMod(target = ExtraCodecs::class, javaFieldWrite = "STRING_RGB_COLOR")
    fun addStringRGBColorInfo(codec: Codec<Int>): Codec<Int> = PackedEncoderColorInfo.wrapCodec(codec, false)
    @JvmStatic
    @CodecMod(target = ExtraCodecs::class, javaFieldWrite = "ARGB_COLOR_CODEC")
    fun addARGBColorInfo(codec: Codec<Int>): Codec<Int> = PackedEncoderColorInfo.wrapCodec(codec, true)
    @JvmStatic
    @CodecMod(target = ExtraCodecs::class, javaFieldWrite = "STRING_ARGB_COLOR")
    fun addStringARGBColorInfo(codec: Codec<Int>): Codec<Int> = PackedEncoderColorInfo.wrapCodec(codec, true)
    @JvmStatic
    @CodecMod(target = TextColor::class, javaFieldWrite = "CODEC")
    fun addTextColorColorInfo(codec: Codec<TextColor>): Codec<TextColor> =
        PackedEncoderColorInfo.wrapCodec(
            codec,
            false,
            TextColor::getValue,
            TextColor::fromRgb,
        ) { ChatFormatting.entries.mapNotNull(TextColor::fromLegacyFormat) } // Lazy, because the map isn't initialized yet when the codec is constructed

    @JvmStatic
    @CodecMod(targetName = $$"Lnet/minecraft/core/component/DataComponentPatch$PatchKey;", javaFieldWrite = "CODEC")
    fun <T> addComponentKeySuggestions(codec: Codec<T>): Codec<T> = CodecSuggestionWrapper(codec, object : SuggestionsProvider {
        override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
            val ids = BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()
            return Stream.concat(
                ids.stream().map { id -> ops.createString("!$id") },
                ids.stream().map { id -> ops.createString(id.toString()) }
            )
        }
    })

    @JvmStatic
    @CodecMod(target = Registry::class, methodName = "referenceHolderWithLifecycle", fieldAccess = ["this"])
    fun <T> addRegistryKeySuggestions(codec: Codec<T>, registry: Registry<*>): Codec<T> = CodecSuggestionWrapper(codec, object : SuggestionsProvider {
        override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> = registry.keys(ops)
    })

    @JvmStatic
    @CodecMod(target = StringRepresentable.StringRepresentableCodec::class, javaFieldWrite = "codec")
    fun <T: StringRepresentable> addStringRepresentableSuggestions(codec: Codec<T>, values: Array<T>): Codec<T> =
        CodecSuggestionWrapper(codec, object : SuggestionsProvider {
            // Technically should be applying name transformer, but I don't think anybody is relying on that since
            // it's not even encoded correctly
            override fun <T : Any> getSuggestions(ops: DynamicOps<T>) =
                Arrays.stream(values).map { ops.createString(it.serializedName) }
        })
    
    @JvmStatic
    @CodecMod(target = TagKey::class, methodName = "codec")
    fun <T: Any> addTagKeySuggestions(codec: Codec<TagKey<T>>, resourceKey: ResourceKey<out Registry<T>>): Codec<TagKey<T>> =
        CodecSuggestionWrapper(codec, object : SuggestionsProvider {
            override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                val owner = (ops as RegistryOps<*>).owner(resourceKey).getOrNull()
                return (owner as? HolderLookup<*>)?.listTagIds()
                        ?.map { key -> ops.createString(key.location().toString()) }
                        ?: Stream.empty()
            }
        })
    @JvmStatic
    @CodecMod(target = TagKey::class, methodName = "hashedCodec")
    fun <T: Any> addHashedTagKeySuggestions(codec: Codec<TagKey<T>>, resourceKey: ResourceKey<out Registry<T>>): Codec<TagKey<T>> =
        CodecSuggestionWrapper(codec, object : SuggestionsProvider {
            override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                val owner = (ops as RegistryOps<*>).owner(resourceKey).getOrNull()
                return (owner as? HolderLookup<*>)?.listTagIds()
                    ?.map { key -> ops.createString('#' + key.location().toString()) }
                    ?: Stream.empty()
            }
        })

    // This Mixin is only for the default Language that is mostly used serverside.
    // There exists a separate Mixin to handle the clientside Language implementation.
    @JvmStatic
    @CodecMod(target = TranslatableContents::class, codecField = "translate")
    fun suggestTranslationNames(codec: PrimitiveCodec<String>): PrimitiveCodec<String> =
        PrimitiveCodecSuggestionWrapper(codec, object : SuggestionsProvider {
            override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> =
                (Language.getInstance() as? LanguageImplAccessor)
                    ?.`val$storage`?.keys?.stream()
                    ?.map<T>(ops::createString)
                    ?: Stream.empty()
        })

    @JvmStatic
    @CodecMod(target = PackFormat::class, methodName = "packCodec")
    fun suggestCurrentPackFormat(codec: MapCodec<InclusiveRange<PackFormat>>, type: PackType): MapCodec<InclusiveRange<PackFormat>> {
        val formatSuggestingCodec = CodecSuggestionWrapper(PackFormat.BOTTOM_CODEC, object : SuggestionsProvider {
            override fun <T : Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                val currentVersion = SharedConstants.getCurrentVersion().packVersion(type)
                return PackFormat.BOTTOM_CODEC.encode(currentVersion, ops, ops.empty()).result().stream()
            }

            override fun <T : Any> suggestionModifier(
                suggestion: ExtraDecoderBehavior.PossibleValue<T>,
                ops: DynamicOps<T>
            ): ExtraDecoderBehavior.PossibleValue<T> =
                suggestion.withCompletionModifier { completion ->
                    completion.detail = "Minecraft's current pack version"
                }
        })
        return RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                formatSuggestingCodec.fieldOf("min_format").onlyDecodeRecord(),
                formatSuggestingCodec.fieldOf("max_format").onlyDecodeRecord(),
                codec.forGetter { it }
            ).apply(instance, { _, _, format -> format })
        }
    }

    @JvmStatic
    @CodecMod(target = TagEntry::class, javaFieldWrite = "CODEC")
    fun verifyTagEntry(codec: Codec<TagEntry>): Codec<TagEntry> = codec.flatXmap( { tagEntry ->
        @Suppress("UNCHECKED_CAST")
        val registry = CURRENT_TAG_ANALYZING_REGISTRY.getOrNull() as Registry<Any>? // Where we're going we don't need: type safety
            ?: return@flatXmap DataResult.success(tagEntry)
        val entryExists = tagEntry.build(object : TagEntry.Lookup<Any> {
            override fun element(key: Identifier, required: Boolean): Any? =
                registry.get(ResourceKey.create(registry.key(), key)).orElse(null)

            // Return null when no tag was found and not null when a tag was found
            override fun tag(key: Identifier): Collection<Any>? =
                registry.get(TagKey.create(registry.key(), key))
                    .map<Collection<Any>> { emptyList() }.orElse(null)

        }) {}
        if(!entryExists)
            return@flatXmap DataResult.error { "Could not find tag entry: $tagEntry" }
        return@flatXmap DataResult.success(tagEntry);
    }, DataResult<TagEntry>::success);

    @JvmStatic
    @CodecMod(target = TagEntry::class, javaFieldWrite = "CODEC")
    fun saveTagEntryRange(codec: Codec<TagEntry>): Codec<TagEntry> = codec.afterDecode(object : AfterDecodeCallback<TagEntry> {
        override fun <TNode: Any> invoke(
            result: TagEntry,
            input: TNode,
            ops: DynamicOps<TNode>,
        ) {
            val rangeMap = TAG_PARSING_ELEMENT_RANGES.getOrNull() ?: return
            (result as StringRangeContainer).`command_crafter$setRange`(rangeMap[input]!!)
        }
    })

    @JvmStatic
    @CodecMod(target = ExtraCodecs::class, javaFieldWrite = "TAG_OR_ELEMENT_ID")
    fun suggestTagEntryNames(codec: Codec<ExtraCodecs.TagOrElementLocation>): Codec<ExtraCodecs.TagOrElementLocation> =
        codec.beforeDecode(object : BeforeDecodeCallback {
            override fun <TNode: Any> invoke(input: TNode, ops: DynamicOps<TNode>) {
                val registry = CURRENT_TAG_ANALYZING_REGISTRY.getOrNull() ?: return
                @Suppress("UNCHECKED_CAST")
                ExtraDecoderBehavior.getCurrentBehavior(ops)?.notePossibleValues(input, {
                    Stream.concat(
                        registry.listElementIds()
                            .map { key -> ops.createString(key!!.identifier().toString()) },
                        registry.listTagIds()
                            .map { key -> ops.createString("#" + key!!.location().toString()) }
                    ).map(ExtraDecoderBehavior<TNode>::PossibleValue)
                })
            }
        })
}