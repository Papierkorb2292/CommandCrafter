package net.papierkorb2292.command_crafter.editor.processing.codecmod

import com.google.common.collect.BiMap
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.ContextChain
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.datafixers.kinds.App
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.*
import com.mojang.serialization.codecs.PrimitiveCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import it.unimi.dsi.fastutil.ints.IntList
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.advancements.predicates.BlockPredicate
import net.minecraft.advancements.predicates.FluidPredicate
import net.minecraft.advancements.predicates.NbtPredicate
import net.minecraft.advancements.predicates.StatePropertiesPredicate
import net.minecraft.advancements.predicates.entity.EntityPredicate
import net.minecraft.advancements.predicates.entity.EntityTypePredicate
import net.minecraft.advancements.triggers.BeeNestDestroyedTrigger
import net.minecraft.advancements.triggers.EnterBlockTrigger
import net.minecraft.advancements.triggers.SlideDownBlockTrigger
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.core.registries.codec.RegistryCodecs
import net.minecraft.locale.Language
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.network.chat.contents.NbtContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.dialog.action.CommandTemplate
import net.minecraft.server.dialog.action.ParsedTemplate
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.pack.PackFormat
import net.minecraft.tags.TagEntry
import net.minecraft.tags.TagKey
import net.minecraft.util.ARGB
import net.minecraft.util.ExtraCodecs
import net.minecraft.util.InclusiveRange
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.item.component.TypedEntityData
import net.minecraft.world.level.SpawnData
import net.minecraft.world.level.block.entity.BeehiveBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.levelgen.structure.templatesystem.rule.blockentity.AppendStatic
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProvider
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders
import net.papierkorb2292.command_crafter.codecmod.CodecMod
import net.papierkorb2292.command_crafter.codecmod.NoDecoderCallbacks
import net.papierkorb2292.command_crafter.editor.debugger.helper.StringRangeContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.plus
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugHandler.Companion.TAG_PARSING_ELEMENT_RANGES
import net.papierkorb2292.command_crafter.editor.processing.*
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper.ContextSuggestionsProvider
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper.SuggestionsProvider
import net.papierkorb2292.command_crafter.editor.processing.helper.*
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.MalformedStringDecoderAnalyzing
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringContent
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringEscaper
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTreeJsonResourceAnalyzer.Companion.CURRENT_TAG_ANALYZING_REGISTRY
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.helper.runWithValueSwap
import net.papierkorb2292.command_crafter.mixin.editor.processing.BeehiveBlockEntityAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.LanguageImplAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.Language.TopLevelClosure
import net.papierkorb2292.command_crafter.parser.helper.NodeAnalyzingExecutor
import net.papierkorb2292.command_crafter.parser.helper.OffsetProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import net.papierkorb2292.command_crafter.parser.number_provider.TermNumberProvider
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlin.jvm.optionals.getOrNull

@Suppress("unused")
object CodecTransformers {

    val EXTRA_CANONICAL_ID = ThreadLocal<Any>()

    @JvmStatic
    @CodecMod(target = Identifier::class, javaFieldRead = "com/mojang/serialization/Codec.STRING")
    fun checkForCanonicalId(codec: PrimitiveCodec<String>): PrimitiveCodec<String> = object : PrimitiveCodec<String> {
        override fun <T : Any> write(ops: DynamicOps<T>, value: String): T =
            codec.write(ops, value)

        override fun <T : Any> read(ops: DynamicOps<T>, input: T): DataResult<String> {
            val nonCanonicalBehavior = ExtraDecoderBehavior.getCurrentBehavior(ops)?.branchBehavior?.nonCanonicalBehavior
            val stringResult = codec.read(ops, input)
            if(nonCanonicalBehavior != ExtraDecoderBehavior.NonCanonicalBehavior.IGNORE && input != EXTRA_CANONICAL_ID.getOrNull())
                return stringResult
            return stringResult.flatMap { string ->
                if(string.isNotEmpty() && ':' in string.subSequence(1, string.length)) DataResult.success(string) // If there is a colon, and it isn't at the first position, there's a namespace
                else DataResult.error { "Canonical id requires a namespace here" }
            }
        }
    }

    @JvmStatic
    @CodecMod(target = ExtraCodecs.LateBoundIdMapper::class, methodName = "codec", fieldAccess = ["idToValue"])
    fun <T, I> addIdMapperSuggestions(codec: Codec<T>, idToValue: BiMap<I, *>, idCodec: Codec<I>): Codec<T> = CodecSuggestionWrapper.simple(codec, object : SuggestionsProvider {
        override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> =
            idToValue.keys.stream().map { idCodec.encodeStart(ops, it).getOrThrow() }
    })

    @JvmStatic
    @CodecMod(target = MapCodec::class, methodName = "orElse(Ljava/util/function/Consumer;Ljava/lang/Object;)Lcom/mojang/serialization/MapCodec;")
    fun addWarningsToOrElse1(codec: MapCodec<*>): MapCodec<*> = codec.decodeAsWarnings()
    @JvmStatic
    @CodecMod(target = MapCodec::class, methodName = "orElse(Ljava/util/function/UnaryOperator;Ljava/lang/Object;)Lcom/mojang/serialization/MapCodec;")
    fun addWarningsToOrElse2(codec: MapCodec<*>): MapCodec<*> = codec.decodeAsWarnings()
    @JvmStatic
    @CodecMod(target = MapCodec::class, methodName = "orElse(Ljava/lang/Object;)Lcom/mojang/serialization/MapCodec;")
    fun addWarningsToOrElse3(codec: MapCodec<*>): MapCodec<*> = codec.decodeAsWarnings()
    @JvmStatic
    @CodecMod(target = MapCodec::class, methodName = "orElseGet(Ljava/util/function/Consumer;Ljava/util/function/Supplier;)Lcom/mojang/serialization/MapCodec;")
    fun addWarningsToOrGet1(codec: MapCodec<*>): MapCodec<*> = codec.decodeAsWarnings()
    @JvmStatic
    @CodecMod(target = MapCodec::class, methodName = "orElseGet(Ljava/util/function/UnaryOperator;Ljava/util/function/Supplier;)Lcom/mojang/serialization/MapCodec;")
    fun addWarningsToOrGet2(codec: MapCodec<*>): MapCodec<*> = codec.decodeAsWarnings()
    @JvmStatic
    @CodecMod(target = MapCodec::class, methodName = "orElseGet(Ljava/util/function/Supplier;)Lcom/mojang/serialization/MapCodec;")
    fun addWarningsToOrGet3(codec: MapCodec<*>): MapCodec<*> = codec.decodeAsWarnings()

    @JvmStatic
    @CodecMod(target = ExtraCodecs::class, javaFieldWrite = "RGB_COLOR_CODEC")
    fun addRGBColorInfo(codec: Codec<Int>): Codec<Int> = PackedEncoderColorInfo.wrapCodec(
        codec.withJsonEncodeAlternative(ExtraCodecs.VECTOR3F.comap {
            Vector3f(
                PackedEncoderColorInfo.roundColorChannel(ARGB.redFloat(it)),
                PackedEncoderColorInfo.roundColorChannel(ARGB.greenFloat(it)),
                PackedEncoderColorInfo.roundColorChannel(ARGB.blueFloat(it))
            )
        }), // Because JSON doesn't support hex
        false,
        nameProvider = { "0x${PackedEncoderColorInfo.colorToHex(it, false)}"} // Use hex instead of list for JSON
    )
    @JvmStatic
    @CodecMod(target = ExtraCodecs::class, javaFieldWrite = "STRING_RGB_COLOR")
    fun addStringRGBColorInfo(codec: Codec<Int>): Codec<Int> = PackedEncoderColorInfo.wrapCodec(codec, false)
    @JvmStatic
    @CodecMod(target = ExtraCodecs::class, javaFieldWrite = "ARGB_COLOR_CODEC")
    fun addARGBColorInfo(codec: Codec<Int>): Codec<Int> = PackedEncoderColorInfo.wrapCodec(
        codec.withJsonEncodeAlternative(ExtraCodecs.VECTOR4F.comap {
            Vector4f(
                PackedEncoderColorInfo.roundColorChannel(ARGB.redFloat(it)),
                PackedEncoderColorInfo.roundColorChannel(ARGB.greenFloat(it)),
                PackedEncoderColorInfo.roundColorChannel(ARGB.blueFloat(it)),
                PackedEncoderColorInfo.roundColorChannel(ARGB.alphaFloat(it))
            )
        }), // Because JSON doesn't support hex
        true,
        nameProvider = { "0x${PackedEncoderColorInfo.colorToHex(it, true)}"} // Use hex instead of list for JSON
    )
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
            { ChatFormatting.entries.mapNotNull(TextColor::fromLegacyFormat) } // Lazy, because the map isn't initialized yet when the codec is constructed
        )
    @JvmStatic
    @CodecMod(target = FireworkExplosion::class, javaFieldWrite = "COLOR_LIST_CODEC")
    fun addFireworkExplosionColorInfo(codec: Codec<IntList>): Codec<IntList> = codec.beforeDecode(object : BeforeDecodeCallback {
        override fun <TNode : Any> invoke(
            input: TNode,
            ops: DynamicOps<TNode>,
        ) {
            if(ExtraDecoderBehavior.getCurrentBehavior(ops)?.nodeAnalyzingTracker == null)
                return
            PackedEncoderColorInfo.wrapCodec(Codec.INT, false, nameProvider = { "0x${PackedEncoderColorInfo.colorToHex(it, false)}"}).listOf().onlyAnalyzingBehavior().decode(ops, input)
        }
    })
    @JvmStatic
    @CodecMod(target = DyeColor::class, javaFieldWrite = "LEGACY_ID_CODEC")
    fun addLegacyDyeColorCodecColorInfo(codec: Codec<DyeColor>): Codec<DyeColor> = PackedEncoderColorInfo.wrapCodec(
        codec,
        false,
        DyeColor::getTextureDiffuseColor, // Use textureDiffuseColor because it is used most commonly by Minecraft
        { rgb ->
            PackedEncoderColorInfo.roundColorLab(DyeColor.entries, rgb, DyeColor::getTextureDiffuseColor)
        },
        { DyeColor.entries },
        { "$it (${it.id})" },
        false
    )

    @JvmStatic
    @CodecMod(targetName = $$"Lnet/minecraft/core/component/DataComponentPatch$PatchKey;", javaFieldWrite = "CODEC")
    fun <T> addComponentKeySuggestions(codec: Codec<T>): Codec<T> = CodecSuggestionWrapper.simple(codec, object : SuggestionsProvider {
        override fun <T: Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
            val ids = BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()
            return Stream.concat(
                ids.stream().map { id -> ops.createString("!$id") },
                ids.stream().map { id -> ops.createString(id.toString()) }
            )
        }
    })

    val REGISTRY_SUGGESTIONS_BLACKLIST = ThreadLocal<Set<Any>>()

    private fun getFilteredIds(registry: Registry<*>, blacklist: Set<Any>?) =
        if(blacklist != null)
            registry.entrySet().stream()
                .filter { it.value !in blacklist }
                .map { it.key.identifier() }
        else
            registry.keySet().stream()

    private fun <T> getRegistrySuggestions(ops: DynamicOps<T>, registry: Registry<*>, namespaceOptional: Boolean, blacklist: Set<Any>?): Stream<T> =
        getIdSuggestions(ops, namespaceOptional) { getFilteredIds(registry, blacklist) }

    private inline fun <T> getIdSuggestions(ops: DynamicOps<T>, namespaceOptional: Boolean, prefix: String = "", ids: () -> Stream<Identifier>): Stream<T> {
        val namespacedStream = ids().map { ops.createString(prefix + it.toString()) }
        if(!namespaceOptional)
            return namespacedStream
        val shortStream = ids().filter { it.namespace == "minecraft" }.map { ops.createString(prefix + it.path) }
        return Stream.concat(namespacedStream, shortStream)
    }

    @JvmStatic
    @CodecMod(target = Registry::class, methodName = "referenceHolderWithLifecycle", fieldAccess = ["this"])
    fun <T> addRegistryKeySuggestions(codec: Codec<T>, registry: Registry<*>): Codec<T> =
        CodecSuggestionWrapper.withContext(codec, idContextGetter(REGISTRY_SUGGESTIONS_BLACKLIST), object : ContextSuggestionsProvider<IdSuggestionContext<Set<Any>>> {
            override fun <T: Any> getSuggestions(ops: DynamicOps<T>, context: IdSuggestionContext<Set<Any>>): Stream<T> =
                getRegistrySuggestions(ops, registry, context.namespaceOptional, context.extra)
        })

    @JvmStatic
    @CodecMod(target = ResourceKey::class, methodName = "codec")
    fun <T : Any, TReg : Any> addResourceKeySuggestions(codec: Codec<T>, registryKey: ResourceKey<out Registry<TReg>>): Codec<T> =
        CodecSuggestionWrapper.withContext(codec, idContextGetter(registryKey), object : ContextSuggestionsProvider<IdSuggestionContext<Registry<TReg>>> {
            override fun <T : Any> getSuggestions(
                ops: DynamicOps<T>,
                context: IdSuggestionContext<Registry<TReg>>,
            ): Stream<T> {
                val (namespaceOptional, registry) = context
                if(registry == null)
                    return Stream.empty()
                return getRegistrySuggestions(ops, registry, namespaceOptional, null)
            }
        })

    @JvmStatic
    @CodecMod(target = StringRepresentable.StringRepresentableCodec::class, javaFieldWrite = "codec")
    fun <T: StringRepresentable> addStringRepresentableSuggestions(codec: Codec<T>, values: Array<T>): Codec<T> {
        val isDyeColor = values.firstOrNull() is DyeColor
        if(isDyeColor) {
            @Suppress("UNCHECKED_CAST")
            return PackedEncoderColorInfo.wrapCodec(
                codec,
                false,
                { (it as DyeColor).textureDiffuseColor }, // Use textureDiffuseColor because it is used most commonly by Minecraft
                { rgb ->
                    PackedEncoderColorInfo.roundColorLab(DyeColor.entries, rgb, DyeColor::getTextureDiffuseColor) as T
                },
                { DyeColor.entries as List<T> }
            )
        }
        return CodecSuggestionWrapper.simple(codec, object : SuggestionsProvider {
            // Technically should be applying name transformer, but I don't think anybody is relying on that since
            // it's not even encoded correctly
            override fun <T : Any> getSuggestions(ops: DynamicOps<T>) =
                Arrays.stream(values).map { ops.createString(it.serializedName) }
        })
    }

    @JvmStatic
    @CodecMod(target = Property::class, javaFieldWrite = "codec", fieldAccess = ["this"])
    fun <T: Comparable<T>> addBlockStatePropertySuggestions(codec: Codec<T>, property: Property<T>): Codec<T> =
        CodecSuggestionWrapper.simple(codec, object : SuggestionsProvider {
            override fun <A : Any> getSuggestions(ops: DynamicOps<A>): Stream<A> = property.possibleValues.map { codec.encodeStart<A>(ops, it).getOrThrow() }.stream()
        })

    @JvmStatic
    @CodecMod(target = ContextIntProviders::class, javaFieldWrite = "DIRECT_CODEC")
    fun disallowIntProviderTermSuggestions(codec: Codec<ContextIntProvider>): Codec<ContextIntProvider> =
        TermNumberProvider.disallowCodecTermSuggestions(codec)

    @JvmStatic
    @CodecMod(target = ContextFloatProviders::class, javaFieldWrite = "DIRECT_CODEC")
    fun disallowFloatProviderTermSuggestions(codec: Codec<ContextFloatProvider>): Codec<ContextFloatProvider> =
        TermNumberProvider.disallowCodecTermSuggestions(codec)

    @JvmStatic
    @CodecMod(target = CompoundTag::class, javaFieldWrite = "CODEC")
    fun decodeEmbeddedNbt(codec: Codec<CompoundTag>): Codec<CompoundTag> = codec.afterDecode(object : AfterDecodeCallback<CompoundTag> {
        override fun <TNode : Any> invoke(result: CompoundTag, input: TNode, ops: DynamicOps<TNode>) {
            val behavior = ExtraDecoderBehavior.getCurrentBehavior(ops) ?: return
            val embeddedDecoder = DataObjectDecoding.getEmbeddedNbtDecoder(input)
            if(embeddedDecoder == null) {
                behavior.markCompletelyAccessed(input)
                return
            }
            behavior.decodeWithBehavior(embeddedDecoder.branchBehaviorModifier, true) {
                embeddedDecoder.decoder.decode(ops, input)
            }
        }
    })

    @JvmStatic
    @CodecMod(target = EntityPredicate::class, javaFieldWrite = "MAP_CODEC")
    fun addEntityPredicateNbtSuggestions(codec: Codec<*>): Codec<*> =
        DataObjectDecoding.wrapWithEmbeddedDecoder(
            codec,
            DataObjectDecoding.convertToDataObjectDecoder(
                EntityTypePredicate.CODEC.encodedFieldOf(Identifier.parse("entity_type"), Identifier.CODEC).map { it.types },
                DataObjectDecoding::getConditionDecoderForEntities,
            ),
            BranchBehaviorProvider.modifierForProvider(BranchBehaviorProvider.getForPathLookup(null)),
            Codec.PASSTHROUGH.encodedFieldOf(Identifier.parse("nbt"), Identifier.CODEC)
        )

    @JvmStatic
    @CodecMod(target = BlockPredicate::class, codecField = "nbt")
    fun addBlockPredicateNbtSuggestions(codec: Codec<BlockPredicate>): Codec<BlockPredicate> =
        DataObjectDecoding.wrapWithEmbeddedDecoder(
            codec,
            DataObjectDecoding.convertToDataObjectDecoder(
                RegistryCodecs.holderSet(Registries.BLOCK).fieldOf("blocks").decoder().decodeParent(),
                DataObjectDecoding::getConditionDecoderForBlocks,
            ),
            BranchBehaviorProvider.modifierForProvider(BranchBehaviorProvider.getForPathLookup(null))
        )

    @JvmStatic
    @CodecMod(target = BlockPredicate::class, codecField = "state")
    fun wrapBlockPredicateStateCodec(codec: Codec<StatePropertiesPredicate>): Codec<StatePropertiesPredicate> =
        wrapStateConditionCodec(codec, false)
    @JvmStatic
    @CodecMod(target = FluidPredicate::class, codecField = "state")
    fun wrapFluidPredicateStateCodec(codec: Codec<StatePropertiesPredicate>): Codec<StatePropertiesPredicate> =
        wrapStateConditionCodec(codec, true)
    @JvmStatic
    @CodecMod(target = EnterBlockTrigger.TriggerInstance::class, codecField = "state")
    fun wrapEnterBlockTriggerStateCodec(codec: Codec<StatePropertiesPredicate>): Codec<StatePropertiesPredicate> =
        wrapStateConditionCodec(codec, false)
    @JvmStatic
    @CodecMod(target = BeeNestDestroyedTrigger.TriggerInstance::class, codecField = "state")
    fun wrapBeeNestDestroyedTriggerStateCodec(codec: Codec<StatePropertiesPredicate>): Codec<StatePropertiesPredicate> =
        wrapStateConditionCodec(codec, false)
    @JvmStatic
    @CodecMod(target = SlideDownBlockTrigger.TriggerInstance::class, codecField = "state")
    fun wrapSlideDownBlockTriggerStateCodec(codec: Codec<StatePropertiesPredicate>): Codec<StatePropertiesPredicate> =
        wrapStateConditionCodec(codec, false)

    fun wrapStateConditionCodec(codec: Codec<StatePropertiesPredicate>, isFluid: Boolean): Codec<StatePropertiesPredicate> {
        // Try out all property codecs that belong to the blocks/fluids
        val stateDefinitionsDecoder: Decoder<List<StateDefinition<*, *>>> = if(isFluid)
            RegistryCodecs.holderSet(Registries.FLUID).optionalFieldOf("fluids").codec()
                .map { fluids ->
                    fluids.getOrNull()?.map { it.value().stateDefinition }
                        ?: BuiltInRegistries.FLUID.listElements().map { it.value().stateDefinition }.toList()
                }
        else
            RegistryCodecs.holderSet(Registries.BLOCK).optionalFieldOf("blocks").codec()
                .map { blocks ->
                    blocks.getOrNull()?.map { it.value().stateDefinition }
                        ?: BuiltInRegistries.BLOCK.listElements().map { it.value().stateDefinition }.toList()
                }
        val stateDecoderProvider = stateDefinitionsDecoder.decodeParent().map { stateDefinitions ->
            val uniqueDefinitions = stateDefinitions.distinctBy { it.properties }
            val propertyNames = stateDefinitions.flatMapTo(mutableSetOf()) { it.properties.map { prop -> prop.name } }

            val propertyCodecs = propertyNames.map { name ->
                val properties = uniqueDefinitions.mapNotNull { definition -> definition.getProperty(name) }
                createCodecForStatePropertyCondition(name, properties)
            }

            // Put everything in a record codec builder so all fields are always attempted
            RecordCodecBuilder.create<Unit> { builder ->
                val fields: List<App<RecordCodecBuilder.Mu<Unit>, Unit>> = propertyCodecs.map {
                    it.forGetter { throw AssertionError() }
                }
                fields.reduceRight { value, acc -> builder.apply2({ left, right -> }, value, acc) }
            }
        }.onlyContextBehavior()

        return codec.conditionalDecode({ VanillaLanguage.IS_ANALYZING_COMMANDS.getOrNull() != true }, Codec.PASSTHROUGH.flatMap { dynamic ->
            val decoder = dynamic.decode(stateDecoderProvider).result()
            val dummyPredicate = StatePropertiesPredicate.Builder.properties().build().get()
            if(decoder.isEmpty)
                return@flatMap DataResult.success(dummyPredicate)
            decoder.get().first.decode(dynamic).map { dummyPredicate }
        })
    }

    fun createCodecForStatePropertyCondition(name: String, properties: List<Property<*>>): MapCodec<Unit> {
        val propertyCodecs: List<Codec<*>> = properties.map { it.codec() }
        val combinedCodecWithWarnings = propertyCodecs.reduceRight { codec, acc -> Codec.either(codec, acc) }
        val vanillaDecoder = StatePropertiesPredicate.ValueMatcher.CODEC.optionalFieldOf(name)
        // Every property can either be a direct value, or a range of values with min and max
        return Codec.either(combinedCodecWithWarnings, RecordCodecBuilder.create {
            it.group(
                combinedCodecWithWarnings.optionalFieldOf("min").forGetter { throw AssertionError() },
                combinedCodecWithWarnings.optionalFieldOf("max").forGetter { throw AssertionError() }
            ).apply(it) { min, max -> }
        }).optionalFieldOf(name)
            .xmap({ }, { throw AssertionError() })
            .decodeAsWarnings(Unit, vanillaDecoder.map { true }) // Only keep errors, if the value actually can't be parsed by the game
    }
    
    @JvmStatic
    @CodecMod(target = TagKey::class, methodName = "codec")
    fun <TReg: Any> addTagKeySuggestions(codec: Codec<TagKey<TReg>>, resourceKey: ResourceKey<out Registry<TReg>>): Codec<TagKey<TReg>> =
        CodecSuggestionWrapper.withContext(codec, idContextGetter(resourceKey), object : ContextSuggestionsProvider<IdSuggestionContext<Registry<TReg>>> {
            override fun <T : Any> getSuggestions(
                ops: DynamicOps<T>,
                context: IdSuggestionContext<Registry<TReg>>
            ): Stream<T> {
                val (namespaceOptional, registry) = context
                if(registry == null) return Stream.empty()
                return getIdSuggestions(ops, namespaceOptional) { registry.listTagIds().map { it.location }}
            }
        })

    @JvmStatic
    @CodecMod(target = TagKey::class, methodName = "hashedCodec")
    fun <TReg: Any> addHashedTagKeySuggestions(codec: Codec<TagKey<TReg>>, resourceKey: ResourceKey<out Registry<TReg>>): Codec<TagKey<TReg>> =
        CodecSuggestionWrapper.withContext(codec, idContextGetter(resourceKey), object : ContextSuggestionsProvider<IdSuggestionContext<Registry<TReg>>> {
            override fun <T : Any> getSuggestions(
                ops: DynamicOps<T>,
                context: IdSuggestionContext<Registry<TReg>>
            ): Stream<T> {
                val (namespaceOptional, registry) = context
                if(registry == null) return Stream.empty()
                return getIdSuggestions(ops, namespaceOptional, "#") { registry.listTagIds().map { it.location }}
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
    @CodecMod(target = TranslatableContents::class, codecField = "translate")
    fun analyzeTranslationComponentContent(codec: PrimitiveCodec<String>): PrimitiveCodec<String> {
        val withCodec = Codec.PASSTHROUGH.listOf().fieldOf("with").codec().decodeParent().onlyContextBehavior()
        val analyzing = MalformedStringDecoderAnalyzing({ dynamic ->
            withCodec.decode(dynamic).resultOrPartial().getOrNull()?.first
        }, { context, result, behavior, reader, string, analyzingBehavior ->
            analyzeTranslationTemplate(result, reader, context, analyzingBehavior)
        })
        return analyzing.wrapCodecWithoutError(codec)
    }

    private val InvalidFormatExceptionType = SimpleCommandExceptionType(Component.literal($$"Invalid format. Allowed are: %s, %<num>$s and %%"))
    private val FormatIndexTooHighExceptionType = Dynamic2CommandExceptionType { index, withLength -> Component.literal("Format index is too high. Index is $index, but 'with' only has $withLength entries") }

    private fun <TNode: Any> analyzeTranslationTemplate(result: AnalyzingResult, reader: DirectiveStringReader<AnalyzingResourceCreator>, with: List<Dynamic<*>>?, analyzingBehavior: ExtraDecoderBehavior.NodeAnalyzingBehavior<TNode>) {
        var incrementalReplacementIndex = 0

        fun isDigit(c: Char) = c in '0'..'9'
        @Throws(CommandSyntaxException::class)
        fun addWithReference(range: StringRange, replacementIndex: Int) {
            if(with == null) return
            if(with.size <= replacementIndex)
                throw FormatIndexTooHighExceptionType.createWithContext(reader, replacementIndex, with.size)
            val dynamic = with[replacementIndex]
            val baseMappingInfo = analyzingBehavior.baseMappingInfo
            result.addMappedActualSyntaxNode(range, object : ActualSyntaxNode {
                override fun getDefinition(cursor: Int): CompletableFuture<ActualSyntaxNode.Definition>? {
                    @Suppress("UNCHECKED_CAST")
                    val targetRange = analyzingBehavior.tryGetNodeRange(dynamic.value as TNode) ?: return null
                    val absoluteRange = baseMappingInfo.cursorMapper.mapToSource(targetRange + baseMappingInfo.readSkippingChars)
                    return CompletableFuture.completedFuture(ActualSyntaxNode.Definition(Either.forLeft(listOf(Location(
                        reader.resourceCreator.sourceFunctionUri,
                        Range(AnalyzingResult.getPositionFromCursor(absoluteRange.start, baseMappingInfo), AnalyzingResult.getPositionFromCursor(absoluteRange.start, baseMappingInfo))
                    ))), false))
                }
                override fun getHover(cursor: Int): CompletableFuture<Hover> = CompletableFuture.completedFuture(Hover(
                    listOf(Either.forLeft(dynamic.value.toString())),
                    result.toFileRange(range)
                ))
            })
        }
        @Throws(CommandSyntaxException::class)
        fun parseFormat(start: Int) {
            if(!reader.canRead())
                throw InvalidFormatExceptionType.createWithContext(reader)
            if(reader.peek() == '%') {
                reader.skip()
                return // This is valid, it's used to insert '%' into the message
            }
            if(reader.peek() == 's') {
                reader.skip()
                addWithReference(StringRange(start, reader.cursor), incrementalReplacementIndex++)
                return
            }
            if(isDigit(reader.peek())) {
                var replacementIndex = 0
                do {
                    replacementIndex = 10 * replacementIndex + reader.read().digitToInt()
                } while(reader.canRead() && isDigit(reader.peek()))
                reader.expect('$')
                reader.expect('s')
                addWithReference(StringRange(start, reader.cursor), replacementIndex)
                return
            }
            throw InvalidFormatExceptionType.createWithContext(reader)
        }
        

        while(reader.canRead()) {
            val start = reader.cursor
            if(reader.read() != '%')
                continue
            try {
                parseFormat(start)
            } catch(e: CommandSyntaxException) {
                result.diagnostics += Diagnostic().apply {
                    message = e.message
                    range = result.toFileRange(StringRange(start, reader.cursor))
                    severity = DiagnosticSeverity.Warning
                }
            }
            val end = reader.cursor
            result.semanticTokens.addMultiline(start, end - start, TokenType.REGEXP, 0)
        }
    }

    @JvmStatic
    @CodecMod(target = PackFormat::class, methodName = "packCodec")
    fun suggestCurrentPackFormat(codec: MapCodec<InclusiveRange<PackFormat>>, type: PackType): MapCodec<InclusiveRange<PackFormat>> {
        val bottomFormatSuggestingCodec = CodecSuggestionWrapper.simple(PackFormat.BOTTOM_CODEC, object : SuggestionsProvider {
            override fun <T : Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                val currentVersion = SharedConstants.getCurrentVersion().packVersion(type)
                // Use TOP_CODEC, because it'll always encode the format as a list
                return PackFormat.TOP_CODEC.encode(currentVersion, ops, ops.empty()).result().stream()
            }

            override fun <T : Any> suggestionModifier(
                suggestion: ExtraDecoderBehavior.PossibleValue<T>,
                ops: DynamicOps<T>
            ): ExtraDecoderBehavior.PossibleValue<T> =
                suggestion.withCompletionModifier { completion ->
                    completion.detail = "Minecraft's current pack version"
                }
        })
        val topFormatSuggestingCodec = CodecSuggestionWrapper.simple(PackFormat.TOP_CODEC, object : SuggestionsProvider {
            override fun <T : Any> getSuggestions(ops: DynamicOps<T>): Stream<T> {
                val currentVersion = SharedConstants.getCurrentVersion().packVersion(type)
                // Only encode the major version so the pack can be forwards compatible
                return Stream.of(ops.createInt(currentVersion.major))
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
                bottomFormatSuggestingCodec.onlyAnalyzingRecord("min_format"),
                topFormatSuggestingCodec.onlyAnalyzingRecord("max_format"),
                codec.forGetterIdent()
            ).apply(instance, { _, _, format -> format })
        }
    }

    @JvmStatic
    @CodecMod(target = TagEntry::class, javaFieldWrite = "CODEC")
    fun verifyTagEntry(codec: Codec<TagEntry>): Codec<TagEntry> = codec.flatXmap( { tagEntry ->
        @Suppress("UNCHECKED_CAST")
        val registry = CURRENT_TAG_ANALYZING_REGISTRY.getOrNull() as HolderLookup.RegistryLookup<Any>? // Where we're going we don't need: type safety
            ?: return@flatXmap DataResult.success(tagEntry)
        @Suppress("UNCHECKED_CAST")
        val registryKey = registry.key() as ResourceKey<out Registry<Any>>
        val entryExists = tagEntry.build(object : TagEntry.Lookup<Any> {
            override fun element(key: Identifier, required: Boolean): Any? =
                registry.get(ResourceKey.create(registryKey, key)).orElse(null)

            // Return null when no tag was found and not null when a tag was found
            override fun tag(key: Identifier): Collection<Any>? =
                registry.get(TagKey.create(registryKey, key))
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
        CodecSuggestionWrapper.withContext(codec, idContextGetter(CURRENT_TAG_ANALYZING_REGISTRY), object : ContextSuggestionsProvider<IdSuggestionContext<HolderLookup.RegistryLookup<*>>> {
            override fun <T : Any> getSuggestions(ops: DynamicOps<T>, context: IdSuggestionContext<HolderLookup.RegistryLookup<*>>): Stream<T> {
                val (namespaceOptional, registry) = context
                if(registry == null) return Stream.empty()
                return Stream.concat(
                    getIdSuggestions(ops, namespaceOptional) { registry.listElementIds().map { it.identifier() } },
                    getIdSuggestions(ops, namespaceOptional, "#") { registry.listTagIds().map { it.location } },
                )
            }
        })

    @JvmStatic
    @CodecMod(target = DataComponents::class, methodName = $$"lambda$static$61", javaFieldRead = "net/minecraft/world/item/component/CustomData.CODEC")
    fun decodeEmbeddedBucketEntityData(codec: Codec<CustomData>): Codec<CustomData> =
        DataObjectDecoding.wrapWithEmbeddedDecoder(codec, unitDecoder(RecordCodecBuilder.create {
            it.group( // Use custom codec, because Minecraft is still using CompoundTags instead of ValueInputs
                Codec.BOOL.lenientOptionalFieldOf("NoAI").forEmptyGetter(),
                Codec.BOOL.lenientOptionalFieldOf("Silent").forEmptyGetter(),
                Codec.BOOL.lenientOptionalFieldOf("NoGravity").forEmptyGetter(),
                Codec.BOOL.lenientOptionalFieldOf("Glowing").forEmptyGetter(),
                Codec.BOOL.lenientOptionalFieldOf("Invulnerable").forEmptyGetter(),
                Codec.FLOAT.lenientOptionalFieldOf("Health").forEmptyGetter(),
                Codec.INT.lenientOptionalFieldOf("Age").forEmptyGetter(),
                Codec.BOOL.lenientOptionalFieldOf("AgeLocked").forEmptyGetter(),
                Codec.INT.lenientOptionalFieldOf("age").forEmptyGetter(), // Sulfur cube uses snake_case
                Codec.BOOL.lenientOptionalFieldOf("age_locked").forEmptyGetter(),
                Codec.LONG.lenientOptionalFieldOf("HuntingCooldown").forEmptyGetter(),
            ).apply(it) { _, _, _, _, _, _, _, _, _, _, _, -> }
        }))

    val TYPED_ENTITY_DATA_FIELD_BLACKLIST = ThreadLocal<List<String>>()

    @JvmStatic
    @CodecMod(target = TypedEntityData::class, methodName = "codec")
    fun <IdType : Any> decodeEmbeddedTypedEntityData(codec: Codec<TypedEntityData<IdType>>, idCodec: Codec<IdType>): Codec<TypedEntityData<IdType>> =
        DataObjectDecoding.wrapWithEmbeddedDecoder(codec, unitDecoder(@NoDecoderCallbacks object : Decoder<Unit> {
            override fun <T : Any> decode(
                ops: DynamicOps<T>,
                input: T,
            ): DataResult<Pair<Unit, T>> {
                val dataObjectDecoding = DataObjectDecoding.getForDecoder(ops)
                if(dataObjectDecoding != null) {
                    val candidates = mutableListOf<IdType>()
                    @Suppress("UNCHECKED_CAST")
                    val nonPlayerIdCodec = if(idCodec == EntityType.CODEC) DataObjectDecoding.NON_PLAYER_ENTITY_TYPE_CODEC as Codec<IdType> else idCodec
                    // Make use of the existing dispatch behavior.
                    var idDispatcher = nonPlayerIdCodec.dispatch(
                        "id",
                        { null },
                        { id ->
                            candidates += id
                            MapCodec.unit(Unit)
                        }
                    )
                    // Add error for missing namespace only when required
                    if(EXTRA_CANONICAL_ID.getOrNull() == null)
                        idDispatcher = idDispatcher.onlyAnalyzingBehavior()
                    idDispatcher.decode(ops, input)
                    val blacklist = (TYPED_ENTITY_DATA_FIELD_BLACKLIST.getOrNull()?.mapTo(mutableSetOf()) {
                        key -> input to ops.createString(key)
                    } ?: setOf()) + (input to ops.createString("id"))
                    val (_, filteredOps) = wrapDynamicOps(ops) { innerOps -> FieldFilteringDynamicOps(innerOps, blacklist) }
                    ExtraDecoderBehavior.swapOps(ops, filteredOps) {
                        dataObjectDecoding.getDecoderForGenericType(candidates)
                            .decode(filteredOps, input)
                    }
                }

                return DataResult.success(Pair(Unit, ops.empty()))
            }
        }), BranchBehaviorProvider.WITH_NON_CANONICAL_KEEP_BEHAVIOR_MODIFIER).markEncodedId("id")

    @JvmStatic
    @CodecMod(target = BeehiveBlockEntity.Occupant::class, codecField = "entity_data")
    fun applyBeeHiveOccupantDataBlacklist(codec: Codec<TypedEntityData<EntityType<*>>>): Codec<TypedEntityData<EntityType<*>>> =
        Codec.of(codec, codec.withThreadLocal(TYPED_ENTITY_DATA_FIELD_BLACKLIST, BeehiveBlockEntityAccessor.getIGNORED_BEE_TAGS()))

    @JvmStatic
    @CodecMod(target = FallingBlockEntity::class, methodName = "readAdditionalSaveData", javaFieldRead = "net/minecraft/nbt/CompoundTag.CODEC")
    fun decodeEmbeddedFallingBlockNbt(codec: Codec<CompoundTag>): Codec<CompoundTag> =
        DataObjectDecoding.wrapWithEmbeddedDecoder(
            codec,
            DataObjectDecoding.convertToDataObjectDecoder(
                BlockState.CODEC.fieldOf("BlockState").decoder().decodeParent().map { it.block },
                DataObjectDecoding::getDecoderForBlock,
            ),
            BranchBehaviorProvider.WITH_NON_CANONICAL_KEEP_BEHAVIOR_MODIFIER
        )

    @JvmStatic
    @CodecMod(target = ServerPlayer::class, methodName = "readAdditionalSaveData", javaFieldRead = "net/minecraft/nbt/CompoundTag.CODEC")
    fun decodeEmbeddedPlayerShoulderEntityNbt(codec: Codec<CompoundTag>): Codec<CompoundTag> =
        DataObjectDecoding.wrapWithEmbeddedDecoder(
            codec,
            DataObjectDecoding.createDataObjectDecoder(DataObjectDecoding::getDispatchingEntityDecoder),
            BranchBehaviorProvider.WITH_NON_CANONICAL_KEEP_BEHAVIOR_MODIFIER
        )

    @JvmStatic
    @CodecMod(target = SpawnData::class, codecField = "entity")
    fun decodeEmbeddedSpawnDataEntityNbt(codec: Codec<CompoundTag>): Codec<CompoundTag> = Codec.of(codec, object : Decoder<CompoundTag> {
        private val analyzingDelegate = DataObjectDecoding.wrapWithEmbeddedDecoder(
            codec,
            DataObjectDecoding.createDataObjectDecoder(DataObjectDecoding::getDispatchingEntityDecoder),
            BranchBehaviorProvider.WITH_NON_CANONICAL_KEEP_BEHAVIOR_MODIFIER
        ).markEncodedId("id").map { CompoundTag() } // Suppress log error for invalid id fields by replacing result with empty compound

        override fun <T : Any> decode(ops: DynamicOps<T>, input: T): DataResult<Pair<CompoundTag, T>> {
            val isAnalyzing = ExtraDecoderBehavior.getCurrentBehavior(ops) != null
            if(!isAnalyzing) return codec.decode(ops, input)
            return analyzingDelegate.decode(ops, input)
        }
    })

    @JvmStatic
    @CodecMod(target = AppendStatic::class, codecField = "data")
    fun decodeProcessorRuleBlockNbt(codec: Codec<CompoundTag>): Codec<CompoundTag> =
        DataObjectDecoding.wrapWithEmbeddedDecoder(
            codec,
            DataObjectDecoding.convertToDataObjectDecoder(
                BlockState.CODEC.fieldOf("output_state").decoder().decodeParent(2).map { it.block },
                DataObjectDecoding::getDecoderForBlock,
            )
        )

    @JvmStatic
    @CodecMod(target = NbtContents::class, codecField = "nbt")
    fun addNbtContentsNbtSuggestions(codec: Codec<NbtPredicate>): Codec<NbtPredicate> =
        DataObjectDecoding.wrapWithEmbeddedDecoder(
            codec,
            DataObjectDecoding.convertToDataObjectDecoder(
                Codec.either(
                    Codec.either(
                        Codec.STRING.fieldOf("entity").codec(),
                        Codec.PASSTHROUGH.fieldOf("block").codec(),
                    ),
                    Codec.STRING.fieldOf("storage").codec()
                ).decodeParent(),
                { dataObjectDecoding, either ->
                    either?.map(
                        { entityOrBlock -> entityOrBlock.map(
                            { entityString ->
                                dataObjectDecoding.getConditionDecoderForEntities(dataObjectDecoding.getEntityChangeCandidates(EntitySelectorParser(StringReader(entityString), true), true))
                            }, { blockInput ->
                                dataObjectDecoding.getConditionDecoderForBlocks(null)
                            })
                        },
                        { storage -> dataObjectDecoding.getConditionDecoderForStorages() }
                    ) ?: dataObjectDecoding.getConditionDecoderForEverything()
                }
            ),
            BranchBehaviorProvider.modifierForProvider(BranchBehaviorProvider.getForPathLookup(null))
        )

    @JvmStatic
    @CodecMod(target = ClickEvent.RunCommand::class, codecField = "command")
    fun analyzeRunCommandClickEvent(codec: Codec<String>): Codec<String> =
        wrapCommandStringCodec(codec, isTemplate = false, isSuggestCommand = false, "")

    @JvmStatic
    @CodecMod(target = ClickEvent.SuggestCommand::class, codecField = "command")
    fun analyzeSuggestCommandClickEvent(codec: Codec<String>): Codec<String> =
        wrapCommandStringCodec(codec, isTemplate = false, isSuggestCommand = true, "")

    @JvmStatic
    @CodecMod(target = CommandTemplate::class, codecField = "template")
    fun analyzeCommandTemplate(codec: Codec<ParsedTemplate>): Codec<ParsedTemplate> =
        wrapCommandStringCodec(codec, isTemplate = true, isSuggestCommand = false, ParsedTemplate.CODEC.parse(JavaOps.INSTANCE, "$(placeholder)").result().orElseThrow())

    private fun <A> wrapCommandStringCodec(codec: Codec<A>, isTemplate: Boolean, isSuggestCommand: Boolean, defaultValue: A): Codec<A> {
        fun getContextChainError(parseResults: ParseResults<*>, input: String): CommandSyntaxException? {
            val flattened = ContextChain.tryFlatten(parseResults.context.build(input))
            return if(flattened.isEmpty) CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseResults.reader) else null
        }

        val analyzing = MalformedStringDecoderAnalyzing({}, { context, result, behavior, reader, string, analyzingBehavior ->
            val isMacro = isTemplate || reader.resourceCreator.macroTargetCursors.any { it >= 0 && it <= reader.string.length } // Check if any of the parents' macros are inside the string
            val hasSlash = reader.canRead() && reader.peek() == '/'
            if(isSuggestCommand) {
                if(!hasSlash && !(isMacro && reader.resourceCreator.macroTargetCursors.contains(0)))
                    return@MalformedStringDecoderAnalyzing // Don't analyze anything if there isn't a slash (but a macro may supply the slash)
            } else if(hasSlash)
                reader.skip()

            if(!isMacro) {
                reader.enterClosure(TopLevelClosure(VanillaLanguage.DEFAULT))
                TermNumberProvider.DISALLOW_EXPRESSIONS.runWithValueSwap(true) {
                    val parseResults = reader.dispatcher.parse(reader, reader.resourceCreator.source)
                    val commandException = Commands.getParseException(parseResults)
                        ?: getContextChainError(parseResults, reader.string)
                    @Suppress("UNCHECKED_CAST")
                    val resultReader = parseResults.reader as DirectiveStringReader<AnalyzingResourceCreator>
                    val commandAnalyzingResult = result.copyInput()
                    VanillaLanguage.DEFAULT.analyzeParsedCommand(parseResults, commandAnalyzingResult, resultReader, NodeAnalyzingExecutor.Immediate)
                    VanillaLanguage.addIllegalCharactersDiagnostic(reader.string, commandAnalyzingResult.mappingInfo, commandAnalyzingResult.diagnostics, DiagnosticSeverity.Error)
                    // Add exception from command as warning. suggest_command doesn't show errors at the end, since the player might be supposed to finish the command.
                    if(commandException != null && (!isSuggestCommand || commandException.cursor < resultReader.string.length)) {
                        commandAnalyzingResult.diagnostics += Diagnostic(
                            resultReader.fileMappingInfo.mapToDiagnosticFileRange(commandException.cursor, resultReader.string.length),
                            commandException.message,
                            DiagnosticSeverity.Warning,
                            null
                        )
                    }
                    result.combineWithActual(commandAnalyzingResult)
                    result.combineWithPotentialWrapped(commandAnalyzingResult) { potentialNode ->
                        // Use the vanilla suggestions request
                        potentialNode.withCompletionThreadLocal(VanillaLanguage.SERVERSIDE_SUGGESTION_GETTER) { cursor, context -> null }
                    }
                }
            } else {
                val absoluteRange = analyzingBehavior.baseMappingInfo.cursorMapper.mapToSource(analyzingBehavior.range + analyzingBehavior.baseMappingInfo.readSkippingChars)
                val relevantLines = AnalyzingResult.getLinesBetweenCursors(absoluteRange.start, absoluteRange.end, analyzingBehavior.baseMappingInfo)

                // Don't add errors if we're in a nested macro, since no errors should show inside macros
                val hasMacroParent = reader.resourceCreator.macroQueue != null
                // The macro parser was already added to the stack by MalformedStringDecoderAnalyzing
                val input = AnalyzingResourceCreator.MacroInput(relevantLines, reader.resourceCreator.macroParserStack.map { it.parser },
                    isTemplate = isTemplate,
                    hasTemplatePrefix = false,
                    addMissingVariablesError = !hasMacroParent,
                    illegalChatCharactersSeverity = if(hasMacroParent) null else if(isTemplate) DiagnosticSeverity.Warning else DiagnosticSeverity.Error // Templates don't check for illegal characters, but it should still be a warning
                )
                val macro = AnalyzingResourceCreator.DecodedMacro(
                    StringContent(
                        reader.string,
                        OffsetProcessedInputCursorMapper(absoluteRange.start).combineWith(reader.cursorMapper), // Map cursor relative to the beginning of the string
                        string.escaper
                    ), absoluteRange
                )
                VanillaLanguage.analyzeMacroString(input, macro, reader.resourceCreator.getMacroParserStartCursors(), StringEscaper.combine(reader.resourceCreator.macroParserStack.map { it.escaper }), null, reader, reader.resourceCreator.source)
            }
        })
        // Don't use the decoder that could return errors while analyzing, all errors are handled by the above analyzer instead. This is
        // because it is not known whether the command is a template, which would be cached so it must be possible to add/remove errors without
        // reparsing this command. Still try to read in a string, so errors for non-string values are added and the string autocompletion is added.
        return analyzing.wrapCodecWithoutError(codec.conditionalDecode({ VanillaLanguage.IS_ANALYZING_COMMANDS.getOrNull() != true }, Codec.STRING.map { defaultValue }))
    }

    private fun idContextGetter(): CodecSuggestionWrapper.ContextGetter<IdSuggestionContext<Nothing>> = { node, behavior ->
        val namespaceOptional = behavior.branchBehavior.nonCanonicalBehavior != ExtraDecoderBehavior.NonCanonicalBehavior.IGNORE
                && node != EXTRA_CANONICAL_ID.getOrNull()
        IdSuggestionContext(namespaceOptional, null)
    }
    private fun <TExtra> idContextGetter(threadLocal: ThreadLocal<TExtra>): CodecSuggestionWrapper.ContextGetter<IdSuggestionContext<TExtra>> = { node, behavior ->
        val namespaceOptional = behavior.branchBehavior.nonCanonicalBehavior != ExtraDecoderBehavior.NonCanonicalBehavior.IGNORE
                && node != EXTRA_CANONICAL_ID.getOrNull()
        IdSuggestionContext(namespaceOptional, threadLocal.getOrNull())
    }
    private fun <TReg : Any> idContextGetter(registry: ResourceKey<out Registry<TReg>>): CodecSuggestionWrapper.ContextGetter<IdSuggestionContext<Registry<TReg>>> = { node, behavior ->
        val namespaceOptional = behavior.branchBehavior.nonCanonicalBehavior != ExtraDecoderBehavior.NonCanonicalBehavior.IGNORE
                && node != EXTRA_CANONICAL_ID.getOrNull()
        IdSuggestionContext(namespaceOptional, behavior.registries?.lookup(registry)?.getOrNull())
    }

    data class IdSuggestionContext<TExtra>(val namespaceOptional: Boolean, val extra: TExtra?)
}