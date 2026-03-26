package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.authlib.GameProfile
import com.mojang.brigadier.context.CommandContext
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceArgument
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderSet
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.Tag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.storage.ValueInput
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.Util
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior
import net.papierkorb2292.command_crafter.editor.processing.codecmod.onlyAnalyzingBehavior
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer
import net.papierkorb2292.command_crafter.editor.processing.helper.IsNonPlayerSelector
import net.papierkorb2292.command_crafter.helper.*
import net.papierkorb2292.command_crafter.mixin.CommandContextAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.BlockEntityTypeAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.EntityTypeAccessor
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import java.util.*
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull

class DataObjectDecoding(private val registries: RegistryAccess) {
    companion object {
        val GET_FOR_REGISTRIES = ::DataObjectDecoding.memoizeLast()

        // Used to replace components in Holder.Reference.components so default components can be accessed outside a world,
        // even when the code accesses the builtin registries directly (for example ItemStack constructors)
        val BUILTIN_REGISTRY_OVERRIDE = ThreadLocal<RegistryAccess>()

        val SELECTOR_TYPE_PREDICATE_TRACKER = ThreadLocal<MutableList<Predicate<Entity>>>()
        val PLAYER_CONSTRUCTOR_LEVEL_OVERRIDE = ThreadLocal<Level>()

        // Applied by CompoundTag.CODEC and TagParser.FLATTENED_CODEC
        val EMBEDDED_NBT_DECODER = ThreadLocal<EmbeddedNbtDecoderData<*>>()

        private val DATA_OBJECT_SOURCE_PACKET_CODEC: StreamCodec<ByteBuf, DataObjectSource> = StreamCodec.composite(
            enumConstantCodec(DataObjectSourceKind::class.java),
            DataObjectSource::kind,
            ByteBufCodecs.STRING_UTF8,
            DataObjectSource::argumentName,
            ::DataObjectSource
        )
        private val DATA_OBJECT_SOURCE_CODEC = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.xmap({ DataObjectSourceKind.valueOf(it) }, { it.toString() }).fieldOf("kind")
                    .forGetter(DataObjectSource::kind),
                Codec.STRING.fieldOf("argument_name").forGetter(DataObjectSource::argumentName),
            ).apply(instance, ::DataObjectSource)
        }

        private val entitiesWithError = mutableSetOf<EntityType<*>>()

        fun registerAdditionalDataTypes() {
            ArgumentTypeAdditionalDataSerializer.registerAdditionalDataType(
                Identifier.fromNamespaceAndPath("command_crafter", "data_object_source"),
                { argumentType ->
                    if(argumentType is DataObjectSourceContainer) {
                        argumentType.`command_crafter$getDataObjectSource`()
                    } else null
                },
                { argumentType, dataObjectSource ->
                    if(argumentType is DataObjectSourceContainer) {
                        argumentType.`command_crafter$setDataObjectSource`(dataObjectSource)
                        true
                    } else false
                }, DATA_OBJECT_SOURCE_PACKET_CODEC.cast(), DATA_OBJECT_SOURCE_CODEC
            )
            ArgumentTypeAdditionalDataSerializer.registerAdditionalDataType(
                Identifier.fromNamespaceAndPath("command_crafter", "non_player_selector"),
                { argumentType ->
                    if(argumentType is IsNonPlayerSelector) {
                        argumentType.`command_crafter$getIsNonPlayerSelector`()
                    } else null
                },
                { argumentType, isNonPlayerSelector ->
                    if(argumentType is IsNonPlayerSelector) {
                        argumentType.`command_crafter$setIsNonPlayerSelector`(isNonPlayerSelector)
                        true
                    } else false
                }, ByteBufCodecs.BOOL.cast(), Codec.BOOL
            )
        }

        fun getForReader(directiveStringReader: DirectiveStringReader<AnalyzingResourceCreator>): DataObjectDecoding {
            return GET_FOR_REGISTRIES(directiveStringReader.resourceCreator.registries)
        }

        fun <TNode> getEmbeddedNbtDecoder(node: TNode): EmbeddedNbtDecoderData<*>? {
            val decoderData = EMBEDDED_NBT_DECODER.getOrNull()
            return if(decoderData?.node == node) decoderData else null
        }

        fun <TResult> wrapWithEmbeddedDecoder(delegate: Codec<TResult>, embeddedDecoderProvider: Decoder<out Decoder<Unit>>, branchBehaviorProvider: BranchBehaviorProvider<Any>): Codec<TResult> = object : Codec<TResult> {
            override fun <T: Any> encode(input: TResult, ops: DynamicOps<T>, prefix: T): DataResult<T> =
                delegate.encode(input, ops, prefix)

            override fun <T: Any> decode(ops: DynamicOps<T>, input: T): DataResult<com.mojang.datafixers.util.Pair<TResult, T>> {
                if(ExtraDecoderBehavior.getCurrentBehavior(ops) == null)
                    return delegate.decode(ops, input)

                val embeddedDecoder = embeddedDecoderProvider.onlyAnalyzingBehavior().decode(ops, input).result()
                    .getOrNull()?.first
                    ?: return delegate.decode(ops, input)
                return decodeWithEmbedding(delegate, ops, input, embeddedDecoder, branchBehaviorProvider)
            }
        }

        fun <TDataObjectRef> convertToDataObjectDecoder(delegate: Decoder<TDataObjectRef>, decoderConverter: (DataObjectDecoding, TDataObjectRef) -> Decoder<Unit>) = object : Decoder<Decoder<Unit>> {
            override fun <T : Any> decode(
                ops: DynamicOps<T>,
                input: T,
            ): DataResult<com.mojang.datafixers.util.Pair<Decoder<Unit>, T>> {
                val registries = ExtraDecoderBehavior.getCurrentBehavior(ops)?.registries ?: return DataResult.error { "data object type decoder needs registries" }
                return delegate.decode(ops, input).map { pair ->
                    pair.mapFirst { ref ->
                        decoderConverter(GET_FOR_REGISTRIES(registries), ref)
                    }
                }
            }
        }

        fun <TNode, TResult> decodeWithEmbedding(delegate: Decoder<TResult>, ops: DynamicOps<TNode>, node: TNode, embeddedDecoder: Decoder<*>, branchBehaviorProvider: BranchBehaviorProvider<Any>): DataResult<com.mojang.datafixers.util.Pair<TResult, TNode>> =
            EMBEDDED_NBT_DECODER.runWithValueSwap(EmbeddedNbtDecoderData(node, embeddedDecoder, branchBehaviorProvider)) {
                delegate.decode(ops, node)
            }
    }

    val dummyWorld = DummyWorld(registries, FeatureFlags.REGISTRY.allFlags())

    val dummyEntities: Map<EntityType<*>, Entity>
    val dummyBlockEntities: Map<Block, BlockEntity>

    init {
        val prevOverride = BUILTIN_REGISTRY_OVERRIDE.get()
        try {
            BUILTIN_REGISTRY_OVERRIDE.set(registries)
            dummyEntities = registries.lookupOrThrow(Registries.ENTITY_TYPE).entrySet().asSequence()
                .mapNotNull { createDummyEntity(it.key.identifier(), it.value) }
                .toMap()
            dummyBlockEntities = registries.lookupOrThrow(Registries.BLOCK_ENTITY_TYPE).entrySet().asSequence()
                .mapNotNull { createDummyBlockEntity(it.key.identifier(), it.value) }
                .flatten()
                .toMap()
        } finally {
            if(prevOverride != null)
                BUILTIN_REGISTRY_OVERRIDE.set(prevOverride)
            else
                BUILTIN_REGISTRY_OVERRIDE.remove()
        }
    }

    fun getDecoderForSource(dataObjectSource: DataObjectSource, context: CommandContext<SharedSuggestionProvider>, reader: DirectiveStringReader<*>): Decoder<Unit>? {
        return when(dataObjectSource.kind) {
            DataObjectSourceKind.ENTITY_SUMMON -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val entity = dummyEntities[ResourceArgument.getEntityType(
                        context as CommandContext<CommandSourceStack>,
                        dataObjectSource.argumentName
                    ).value()] ?: return null
                    DynamicOpsReadView.getReadDecoder(registries) { input ->
                        analyzeEntity(entity, input)
                    }
                } catch(_: IllegalArgumentException) {
                    // No entity argument found, maybe it's macro. Decoder should try out all entities
                    DynamicOpsReadView.getReadDecoder(registries) { input ->
                        for(entity in dummyEntities.values) {
                            if(entity !is ServerPlayer)
                                analyzeEntity(entity, input)
                        }
                    }
                }
            }
            DataObjectSourceKind.ENTITY_CHANGE -> {
                val selectorArgument = (context as CommandContextAccessor).arguments[dataObjectSource.argumentName]
                val validEntities = if(selectorArgument != null) {
                    val selectorInput = selectorArgument.range.get(context.input)
                    val selectorInputReader = reader.copy()
                    selectorInputReader.toCompleted()
                    selectorInputReader.string = selectorInput
                    selectorInputReader.cursor = 0
                    val selectorParser = EntitySelectorParser(selectorInputReader, true)
                    getEntityChangeCandidates(selectorParser, false)
                } else {
                    dummyEntities.values
                }
                DynamicOpsReadView.getReadDecoder(registries) { input ->
                    for(entity in validEntities) {
                        analyzeEntity(entity, input)
                    }
                }
            }
            DataObjectSourceKind.BLOCK_ENTITY_CHANGE -> {
                // It is not possible to know which block entity it is. Decoder should try out all blocks
                DynamicOpsReadView.getReadDecoder(registries) { input ->
                    for(blockEntity in dummyBlockEntities.values) {
                        analyzeBlockEntity(blockEntity, input)
                    }
                }
            }
        }
    }

    fun getEntityChangeCandidates(selectorParser: EntitySelectorParser, includePlayers: Boolean): Collection<Entity> {
        val predicates = mutableListOf<Predicate<Entity>>()
        SELECTOR_TYPE_PREDICATE_TRACKER.runWithValue(predicates) {
            selectorParser.parse()
        }
        val selector = selectorParser.selector
        if(!selector.includesEntities()) {
            if(includePlayers) {
                val player = dummyEntities[EntityType.PLAYER]!!
                if(predicates.all { predicate -> predicate.test(player) })
                    return listOf(player)
            }
            return listOf()
        }
        return dummyEntities.values.filter { entity ->
            (entity !is ServerPlayer || includePlayers) && predicates.all { predicate -> predicate.test(entity) }
        }
    }

    fun getDecoderForBlock(block: Block): Decoder<Unit>? {
        val blockEntity = dummyBlockEntities[block] ?: return null
        return DynamicOpsReadView.getReadDecoder(registries) { input ->
            analyzeBlockEntity(blockEntity, input)
        }
    }

    fun getConditionDecoderForBlocks(blocks: HolderSet<Block>?): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries) { valueInput ->
            if(blocks == null || !blocks.isBound)
                dummyBlockEntities.values.distinct().forEach { analyzeBlockEntity(it, valueInput) }
            else
                blocks.stream()
                    .map { dummyBlockEntities[it.value()] }
                    .filter { it != null }
                    .distinct()
                    .forEach {
                        analyzeBlockEntity(it!!, valueInput)
                    }
        }

    fun getConditionDecoderForEntities(entityTypes: HolderSet<EntityType<*>>?): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries) { valueInput ->
            if(entityTypes == null || !entityTypes.isBound)
                dummyEntities.values.forEach { analyzeEntity(it, valueInput) }
            else
                entityTypes.stream()
                    .map { dummyEntities[it.value()] }
                    .filter { it != null }
                    .forEach {
                        analyzeEntity(it!!, valueInput)
                    }
        }

    private fun analyzeBlockEntity(blockEntity: BlockEntity, valueInput: ValueInput) {
        try {
            blockEntity.loadWithComponents(valueInput)
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.error("Error analyzing block entity nbt for type ${registries.lookupOrThrow(Registries.BLOCK_ENTITY_TYPE).getKey(blockEntity.type)}", e)
        }
    }

    private fun analyzeEntity(entity: Entity, valueInput: ValueInput) {
        if(entity.type in entitiesWithError)
            return // Don't analyze entities that threw an error, because repeatedly throwing these errors can be very slow
        try {
            if(entity is ServerPlayer)
                valueInput.read("SelectedItem", ItemStack.CODEC)
            entity.load(valueInput)
        } catch(e: Throwable) {
            entitiesWithError += entity.type
            CommandCrafter.LOGGER.error("Error analyzing entity nbt for type ${registries.lookupOrThrow(Registries.ENTITY_TYPE).getKey(entity.type)}. Entity will be ignored in the future.", e)
        }
    }

    private fun <T : Entity> createDummyEntity(id: Identifier, entityType: EntityType<T>): Pair<EntityType<T>, Entity>? {
        try {
            if(entityType == EntityType.PLAYER) {
                val entity = PLAYER_CONSTRUCTOR_LEVEL_OVERRIDE.runWithValueSwap(dummyWorld) {
                    ServerPlayer(
                        Util.nullIsFine<MinecraftServer>(null), // Handled with mixins
                        Util.nullIsFine<ServerLevel>(null),
                        GameProfile(UUID.randomUUID(), "DummyPlayer"),
                        ClientInformation.createDefault()
                    )
                }
                return entityType to entity
            }
            @Suppress("UNCHECKED_CAST")
            val entity = (entityType as EntityTypeAccessor<T>).factory.create(entityType, dummyWorld)
            if(entity == null) {
                CommandCrafter.LOGGER.warn("Couldn't create dummy entity of type $id: Factory returned null")
                return null
            }
            return entityType to entity
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.warn("Error creating dummy entity of type $id", e)
            return null
        }
    }


    private fun <T : BlockEntity> createDummyBlockEntity(id: Identifier, blockEntityType: BlockEntityType<T>): Sequence<Pair<Block, BlockEntity>>? {
        try {
            @Suppress("UNCHECKED_CAST")
            val accessor = blockEntityType as BlockEntityTypeAccessor<T>
            val blockEntity = accessor.factory.create(BlockPos.ZERO, accessor.validBlocks.first().defaultBlockState()) ?: return null
            return accessor.validBlocks.asSequence().map { it to blockEntity }
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.warn("Error creating dummy block entity of type $id", e)
            return null
        }
    }

    data class DataObjectSource(val kind: DataObjectSourceKind, val argumentName: String) {
        fun getNBTBranchBehavior(): BranchBehaviorProvider<Tag> = when(kind) {
            DataObjectSourceKind.ENTITY_SUMMON -> BranchBehaviorProvider.Decode
            DataObjectSourceKind.ENTITY_CHANGE -> BranchBehaviorProvider.getNBTMerge()
            DataObjectSourceKind.BLOCK_ENTITY_CHANGE -> BranchBehaviorProvider.getNBTMerge()
        }
    }

    data class EmbeddedNbtDecoderData<TNode>(val node: TNode, val decoder: Decoder<*>, val branchBehavior: BranchBehaviorProvider<Any>)

    enum class DataObjectSourceKind {
        ENTITY_SUMMON,
        ENTITY_CHANGE,
        BLOCK_ENTITY_CHANGE,
    }
}
