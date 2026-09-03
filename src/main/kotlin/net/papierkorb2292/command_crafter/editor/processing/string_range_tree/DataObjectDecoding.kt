package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.authlib.GameProfile
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.serialization.*
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.advancements.predicates.NbtPredicate
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
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.storage.ValueInput
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.Util
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.ArgumentTypeAdditionalDataSerializer
import net.papierkorb2292.command_crafter.editor.processing.BranchBehaviorProvider
import net.papierkorb2292.command_crafter.editor.processing.codecmod.*
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

        val NON_PLAYER_ENTITY_TYPE_CODEC: Codec<EntityType<*>> = Codec.of(
            EntityType.CODEC,
            EntityType.CODEC.withThreadLocal(CodecTransformers.REGISTRY_SUGGESTIONS_BLACKLIST, setOf(EntityTypes.PLAYER))
        )

        // Used to replace components in Holder.Reference.components so default components can be accessed outside a world,
        // even when the code accesses the builtin registries directly (for example ItemStack constructors)
        val BUILTIN_REGISTRY_OVERRIDE = ThreadLocal<RegistryAccess>()

        val SELECTOR_TYPE_PREDICATE_TRACKER = ThreadLocal<MutableList<Predicate<Entity>>>()
        val PLAYER_CONSTRUCTOR_LEVEL_OVERRIDE = ThreadLocal<Level>()

        // Applied by CompoundTag.CODEC and TagParser.FLATTENED_CODEC
        val EMBEDDED_NBT_DECODER = ThreadLocal<EmbeddedNbtDecoderData<*>>()
        // Applied by the nbt= selector option
        val SELECTOR_NBT_DECODER = ThreadLocal<Decoder<*>>()

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
        private var playerThrewError = false

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
        fun getForDecoder(ops: DynamicOps<*>): DataObjectDecoding? {
            val registries = ExtraDecoderBehavior.getCurrentBehavior(ops)?.registries ?: return null
            return GET_FOR_REGISTRIES(registries)
        }

        fun <TNode> getEmbeddedNbtDecoder(node: TNode): EmbeddedNbtDecoderData<*>? {
            val decoderData = EMBEDDED_NBT_DECODER.getOrNull()
            return if(decoderData?.node == node) decoderData else null
        }

        fun <TResult> wrapWithEmbeddedDecoder(
            delegate: Codec<TResult>,
            embeddedDecoderProvider: Decoder<out Decoder<*>>,
            branchBehaviorModifier: BranchBehaviorProvider.BranchBehaviorModifier = BranchBehaviorProvider.DEFAULT_BEHAVIOR_MODIFIER,
            affectedNodeSelector: Decoder<Dynamic<*>> = Codec.PASSTHROUGH
        ): Codec<TResult> = object : Codec<TResult> {
            override fun <T: Any> encode(input: TResult, ops: DynamicOps<T>, prefix: T): DataResult<T> =
                delegate.encode(input, ops, prefix)

            override fun <T: Any> decode(ops: DynamicOps<T>, input: T): DataResult<com.mojang.datafixers.util.Pair<TResult, T>> {
                if(ExtraDecoderBehavior.getCurrentBehavior(ops) == null)
                    return delegate.decode(ops, input)

                val embeddedDecoder = embeddedDecoderProvider.onlyContextBehavior().decode(ops, input).result()
                    .getOrNull()?.first
                    ?: return delegate.decode(ops, input)
                val affectedNode = affectedNodeSelector.onlyContextBehavior().decode(ops, input).result()
                    .getOrNull()?.first
                    ?: return delegate.decode(ops, input)
                return delegate.withThreadLocal(EMBEDDED_NBT_DECODER, EmbeddedNbtDecoderData(affectedNode.value, embeddedDecoder, branchBehaviorModifier))
                    .decode(ops, input)
            }
        }

        fun <TDataObjectRef> convertToDataObjectDecoder(delegate: Decoder<TDataObjectRef>, decoderConverter: (DataObjectDecoding, TDataObjectRef?) -> Decoder<Unit>) = object : Decoder<Decoder<Unit>> {
            override fun <T : Any> decode(
                ops: DynamicOps<T>,
                input: T,
            ): DataResult<com.mojang.datafixers.util.Pair<Decoder<Unit>, T>> {
                val dataObjectDecoding = getForDecoder(ops) ?: return DataResult.error { "missing data object type decoder" }
                return DataResult.success(delegate.decode(ops, input).mapOrElse({ pair ->
                    pair.mapFirst { ref ->
                        decoderConverter(dataObjectDecoding, ref)
                    }
                }, { com.mojang.datafixers.util.Pair(decoderConverter(dataObjectDecoding, null), ops.empty()) }))
            }
        }

        fun createDataObjectDecoder(decoderSupplier: (DataObjectDecoding) -> Decoder<Unit>): Decoder<Decoder<Unit>> =
            convertToDataObjectDecoder(unitDecoder(Unit)) { dataObjectDecoding, _ -> decoderSupplier(dataObjectDecoding) }
    }

    val dummyWorld = DummyWorld(registries, FeatureFlags.REGISTRY.allFlags())

    val dummyEntities: Map<EntityType<*>, Entity>
    var fallbackPlayer: FallbackPlayer? = null
    val dummyBlockEntitiesByType: Map<BlockEntityType<*>, BlockEntity>
    val dummyBlockEntitiesByBlock: Map<Block, BlockEntity>

    init {
        val prevOverride = BUILTIN_REGISTRY_OVERRIDE.get()
        try {
            BUILTIN_REGISTRY_OVERRIDE.set(registries)
            dummyEntities = registries.lookupOrThrow(Registries.ENTITY_TYPE).entrySet().asSequence()
                .mapNotNull { createDummyEntity(it.key.identifier(), it.value) }
                .toMap()
            dummyBlockEntitiesByType = registries.lookupOrThrow(Registries.BLOCK_ENTITY_TYPE).entrySet().asSequence()
                .mapNotNull { createDummyBlockEntity(it.key.identifier(), it.value) }
                .toMap()
            dummyBlockEntitiesByBlock = dummyBlockEntitiesByType.entries.flatMap { (type, entity) ->
                (type as BlockEntityTypeAccessor<*>).validBlocks.map { it to entity }
            }.toMap()
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
                        analyzeEntity(entity, input, true)
                    }
                } catch(_: IllegalArgumentException) {
                    // No entity argument found, maybe it's macro. Decoder should try out all entities
                    DynamicOpsReadView.getReadDecoder(registries) { input ->
                        for(entity in dummyEntities.values) {
                            if(entity !is ServerPlayer)
                                analyzeEntity(entity, input, true)
                        }
                    }
                }
            }
            DataObjectSourceKind.ENTITY_CHANGE, DataObjectSourceKind.ENTITY_LOOKUP, DataObjectSourceKind.MUTATING_ENTITY_LOOKUP -> {
                val selectorArgument = (context as CommandContextAccessor).arguments[dataObjectSource.argumentName]
                val validEntities = if(selectorArgument != null) {
                    val selectorInput = selectorArgument.range.get(context.input)
                    val selectorInputReader = reader.copy()
                    selectorInputReader.toCompleted()
                    selectorInputReader.string = selectorInput
                    selectorInputReader.cursor = 0
                    val selectorParser = EntitySelectorParser(selectorInputReader, true)
                    getEntityChangeCandidates(selectorParser, dataObjectSource.kind == DataObjectSourceKind.ENTITY_LOOKUP)
                } else {
                    dummyEntities.values
                }
                DynamicOpsReadView.getReadDecoder(registries) { input ->
                    for(entity in validEntities) {
                        analyzeEntity(entity, input, dataObjectSource.kind == DataObjectSourceKind.ENTITY_LOOKUP)
                    }
                }
            }
            DataObjectSourceKind.BLOCK_ENTITY_CHANGE, DataObjectSourceKind.BLOCK_ENTITY_LOOKUP, DataObjectSourceKind.MUTATING_BLOCK_ENTITY_LOOKUP -> {
                // It is not possible to know which block entity it is. Decoder should try out all blocks
                DynamicOpsReadView.getReadDecoder(registries) { input ->
                    for(blockEntity in dummyBlockEntitiesByType.values) {
                        analyzeBlockEntity(blockEntity, input)
                    }
                }
            }
            DataObjectSourceKind.PATH_SET_MUTATION, DataObjectSourceKind.PATH_MERGE_MUTATION, DataObjectSourceKind.PATH_APPEND_MUTATION -> {
                // Use the decoder from the path argument
                val pathCommandNode = context.nodes.firstOrNull { it.node.name == dataObjectSource.argumentName }?.node
                val delegateSource = ((pathCommandNode as? ArgumentCommandNode<*, *>)?.type as? DataObjectSourceContainer)?.`command_crafter$getDataObjectSource`()
                    ?: return null
                getDecoderForSource(delegateSource, context, reader)
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
                val player = dummyEntities[EntityTypes.PLAYER]!!
                if(predicates.all { predicate -> predicate.test(player) })
                    return listOf(player)
            }
            return listOf()
        }
        return dummyEntities.values.filter { entity ->
            (entity !is ServerPlayer || includePlayers) && predicates.all { predicate -> predicate.test(entity) }
        }
    }

    fun getDecoderForBlock(block: Block?): Decoder<Unit> {
        return DynamicOpsReadView.getReadDecoder(registries) { input ->
            val blockEntity = dummyBlockEntitiesByBlock[block]
            if(blockEntity == null)
                dummyBlockEntitiesByType.values.forEach { analyzeBlockEntity(it, input) }
            else
                analyzeBlockEntity(blockEntity, input)
        }
    }

    fun getConditionDecoderForBlocks(blocks: HolderSet<Block>?): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries) { valueInput ->
            if(blocks == null || !blocks.isBound)
                dummyBlockEntitiesByType.values.forEach { analyzeBlockEntity(it, valueInput) }
            else
                blocks.stream()
                    .map { dummyBlockEntitiesByBlock[it.value()] }
                    .filter { it != null }
                    .distinct()
                    .forEach {
                        analyzeBlockEntity(it!!, valueInput)
                    }
        }

    fun getConditionDecoderForEntities(entityTypes: HolderSet<EntityType<*>>?): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries) { valueInput ->
            if(entityTypes == null || !entityTypes.isBound)
                dummyEntities.values.forEach { analyzeEntity(it, valueInput, true) }
            else
                entityTypes.stream()
                    .map { dummyEntities[it.value()] }
                    .filter { it != null }
                    .forEach {
                        analyzeEntity(it!!, valueInput, true)
                    }
        }

    fun getConditionDecoderForEntities(entities: Iterable<Entity>): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries) { valueInput ->
            for(entity in entities) {
                analyzeEntity(entity, valueInput, true)
            }
        }

    fun getConditionDecoderForEverything(): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries) { valueInput ->
            dummyEntities.values.forEach { analyzeEntity(it, valueInput, true) }
            dummyBlockEntitiesByType.values.forEach { analyzeBlockEntity(it, valueInput) }
            ExtraDecoderBehavior.markCompletelyAccessed(valueInput.dynamic) // For storages
        }

    fun getConditionDecoderForStorages(): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries) { valueInput ->
            // Storages don't have any type info yet
            ExtraDecoderBehavior.markCompletelyAccessed(valueInput.dynamic)
        }

    fun <IdType> getDecoderForGenericType(types: Iterable<IdType>): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries) { valueInput ->
            for(type in types) {
                when(type) {
                    is BlockEntityType<*> -> {
                        val blockEntity = dummyBlockEntitiesByType[type] ?: continue
                        analyzeBlockEntity(blockEntity, valueInput)
                    }
                    is EntityType<*> -> {
                        val entity = dummyEntities[type] ?: continue
                        analyzeEntity(entity, valueInput, true)
                    }
                }
            }
        }

    fun getDispatchingEntityDecoder(): Decoder<Unit> =
        DynamicOpsReadView.getReadDecoder(registries, ::readDispatchingEntity)

    fun readDispatchingEntity(valueInput: DynamicOpsReadView<*>) {
        if(!valueInput.deduplicationMarkers.add("readDispatchingEntity"))
            return // Already done
        valueInput.alwaysReturnEmpty = false
        val macroCheckedId = valueInput.lateAdditionRunner.acceptLateAddition {
            valueInput.dynamic.read(NON_PLAYER_ENTITY_TYPE_CODEC.withMacroCheck().fieldOf("id").codec()).result().getOrNull()
        }
        val id = macroCheckedId?.result?.result()?.getOrNull()
        valueInput.alwaysReturnEmpty = true
        val entity = dummyEntities[id]
        if(entity == null && ExtraDecoderBehavior.getCurrentBehavior(valueInput.dynamic.ops)?.branchBehavior?.isAllPossibleEncoded() == true || macroCheckedId?.hasMacro ?: false) {
            dummyEntities.values.forEach { if(it !is ServerPlayer) analyzeEntity(it, valueInput, true) }
            return
        } else if(entity != null)
            analyzeEntity(entity, valueInput, true)
    }

    fun analyzeBlockEntity(blockEntity: BlockEntity, valueInput: ValueInput) {
        try {
            synchronized(this) {
                blockEntity.loadWithComponents(valueInput)
            }
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.error("Error analyzing block entity nbt for type ${registries.lookupOrThrow(Registries.BLOCK_ENTITY_TYPE).getKey(blockEntity.type)}", e)
        }
    }

    fun analyzeEntity(entity: Entity, valueInput: DynamicOpsReadView<*>, includePassengers: Boolean) {
        if(entity.type in entitiesWithError)
            return // Don't analyze entities that threw an error, because repeatedly throwing these errors can be very slow
        if(!valueInput.deduplicationMarkers.add(entity))
            return // Already done

        val actualEntity = if(entity is ServerPlayer && playerThrewError) {
            if(fallbackPlayer == null)
                fallbackPlayer = createFallbackPlayer()
            fallbackPlayer!!
        } else entity

        try {
            if(actualEntity is Player) // Include both ServerPlayer and FallbackPlayer
                valueInput.read(NbtPredicate.SELECTED_ITEM_TAG, ItemStack.CODEC)
            if(includePassengers && valueInput.deduplicationMarkers.add("Passengers")) {
                valueInput.childrenListOrEmpty(Entity.TAG_PASSENGERS).forEach {
                    readDispatchingEntity(it as DynamicOpsReadView<*>)
                }
            }
            synchronized(this) {
                actualEntity.load(valueInput)
            }
        } catch(e: Throwable) {
            if(actualEntity is ServerPlayer) {
                // Switch to player fallback
                playerThrewError = true
                CommandCrafter.LOGGER.error("Error analyzing server player nbt. Switching to fallback", e)
            } else {
                // For other entities, or if the fallback player also threw an error, ignore them in the future
                entitiesWithError += entity.type
                CommandCrafter.LOGGER.error("Error analyzing entity nbt for type ${registries.lookupOrThrow(Registries.ENTITY_TYPE).getKey(entity.type)}. Entity will be ignored in the future.", e)
            }
        }
    }

    private fun <T : Entity> createDummyEntity(id: Identifier, entityType: EntityType<T>): Pair<EntityType<T>, Entity>? {
        try {
            if(entityType == EntityTypes.PLAYER) {
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
            if(entityType == EntityTypes.PLAYER) {
                CommandCrafter.LOGGER.info("Using fallback player instead")
                val fallback = createFallbackPlayer()
                if(fallback != null)
                    return entityType to fallback
            }
            return null
        }
    }

    private fun createFallbackPlayer(): FallbackPlayer? =
        try {
            FallbackPlayer(dummyWorld, GameProfile(UUID.randomUUID(), "DummyPlayer"))
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.warn("Error creating fallback dummy player", e)
            null
        }


    private fun <T : BlockEntity> createDummyBlockEntity(id: Identifier, blockEntityType: BlockEntityType<T>): Pair<BlockEntityType<*>, BlockEntity>? {
        try {
            @Suppress("UNCHECKED_CAST")
            val accessor = blockEntityType as BlockEntityTypeAccessor<T>
            val blockEntity = accessor.factory.create(BlockPos.ZERO, accessor.validBlocks.first().defaultBlockState()) ?: return null
            return blockEntityType to blockEntity
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.warn("Error creating dummy block entity of type $id, please report this to the developer Papierkorb2292 and include a list of installed mods", e)
            return null
        }
    }

    data class DataObjectSource(val kind: DataObjectSourceKind, val argumentName: String) {
        fun getNBTBranchBehavior(pathMutatingValue: Tag? = null): BranchBehaviorProvider<Tag> = when(kind) {
            DataObjectSourceKind.ENTITY_SUMMON
                -> BranchBehaviorProvider.Decode
            DataObjectSourceKind.ENTITY_CHANGE, DataObjectSourceKind.BLOCK_ENTITY_CHANGE
                -> BranchBehaviorProvider.getNBTMerge()
            DataObjectSourceKind.ENTITY_LOOKUP, DataObjectSourceKind.MUTATING_ENTITY_LOOKUP, DataObjectSourceKind.BLOCK_ENTITY_LOOKUP, DataObjectSourceKind.MUTATING_BLOCK_ENTITY_LOOKUP
                -> BranchBehaviorProvider.getForPathLookup(null)
            DataObjectSourceKind.PATH_SET_MUTATION, DataObjectSourceKind.PATH_APPEND_MUTATION
                -> BranchBehaviorProvider.getForPathLookup(pathMutatingValue)
            DataObjectSourceKind.PATH_MERGE_MUTATION
                -> BranchBehaviorProvider.getNBTMergePathLookup(pathMutatingValue)
        }

        fun isPathReference(): Boolean =
            kind == DataObjectSourceKind.PATH_SET_MUTATION || kind == DataObjectSourceKind.PATH_MERGE_MUTATION || kind == DataObjectSourceKind.PATH_APPEND_MUTATION
    }

    data class EmbeddedNbtDecoderData<TNode>(val node: TNode, val decoder: Decoder<*>, val branchBehaviorModifier: BranchBehaviorProvider.BranchBehaviorModifier)

    enum class DataObjectSourceKind {
        ENTITY_SUMMON,
        ENTITY_CHANGE,
        BLOCK_ENTITY_CHANGE,
        ENTITY_LOOKUP,
        MUTATING_ENTITY_LOOKUP,
        BLOCK_ENTITY_LOOKUP,
        MUTATING_BLOCK_ENTITY_LOOKUP,
        PATH_MERGE_MUTATION,
        PATH_SET_MUTATION,
        PATH_APPEND_MUTATION,
    }
}
