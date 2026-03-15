package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.CommandContext
import com.mojang.serialization.Codec
import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.ResourceArgument
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.Tag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer
import net.papierkorb2292.command_crafter.helper.DummyWorld
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.helper.runWithValue
import net.papierkorb2292.command_crafter.mixin.CommandContextAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.BlockEntityTypeAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.EntityTypeAccessor
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import java.util.function.Predicate

class DataObjectDecoding(private val registries: RegistryAccess) {
    companion object {
        val GET_FOR_REGISTRIES = ::DataObjectDecoding.memoizeLast()
        // Used to replace components in Holder.Reference.components so default components can be accessed outside a world,
        // even when the code accesses the builtin registries directly (for example ItemStack constructors)
        val BUILTIN_REGISTRY_OVERRIDE = ThreadLocal<RegistryAccess>()

        val SELECTOR_TYPE_PREDICATE_TRACKER = ThreadLocal<MutableList<Predicate<Entity>>>()

        private val DATA_OBJECT_SOURCE_PACKET_CODEC: StreamCodec<ByteBuf, DataObjectSource> = StreamCodec.composite(
            enumConstantCodec(DataObjectSourceKind::class.java),
            DataObjectSource::kind,
            ByteBufCodecs.STRING_UTF8,
            DataObjectSource::argumentName,
            ::DataObjectSource
        )
        private val DATA_OBJECT_SOURCE_CODEC = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.xmap({ DataObjectSourceKind.valueOf(it) }, { it.toString() }).fieldOf("kind").forGetter(DataObjectSource::kind),
                Codec.STRING.fieldOf("argument_name").forGetter(DataObjectSource::argumentName),
            ).apply(instance, ::DataObjectSource)
        }

        fun registerDataObjectSourceAdditionalDataType() {
            ArgumentTypeAdditionalDataSerializer.registerAdditionalDataType(
                Identifier.fromNamespaceAndPath("command_crafter","data_object_source"),
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
        }

        fun getForReader(directiveStringReader: DirectiveStringReader<AnalyzingResourceCreator>): DataObjectDecoding? {
            return GET_FOR_REGISTRIES(directiveStringReader.resourceCreator.registries)
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
                        entity.load(input)
                    }
                } catch(_: IllegalArgumentException) {
                    // No entity argument found, maybe it's macro. Decoder should try out all entities
                    DynamicOpsReadView.getReadDecoder(registries) { input ->
                        for(entity in dummyEntities.values) {
                            entity.load(input)
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
                    getEntityChangeCandidates(selectorParser)
                } else {
                    dummyEntities.values
                }
                DynamicOpsReadView.getReadDecoder(registries) { input ->
                    for(entity in validEntities) {
                        entity.load(input)
                    }
                }
            }
            DataObjectSourceKind.BLOCK_ENTITY_CHANGE -> {
                // It is not possible to know which block entity it is. Decoder should try out all blocks
                DynamicOpsReadView.getReadDecoder(registries) { input ->
                    for(blockEntity in dummyBlockEntities.values) {
                        blockEntity.loadWithComponents(input)
                    }
                }
            }
        }
    }

    private fun getEntityChangeCandidates(selectorParser: EntitySelectorParser): Collection<Entity> {
        val predicates = mutableListOf<Predicate<Entity>>()
        SELECTOR_TYPE_PREDICATE_TRACKER.runWithValue(predicates) {
            selectorParser.parse()
        }
        val selector = selectorParser.selector
        if(!selector.includesEntities())
            return listOf() // Selector includes only players and their data can't be modified
        return dummyEntities.values.filter { entity ->
            predicates.all { predicate -> predicate.test(entity) }
        }
    }

    fun getDecoderForBlock(block: Block): Decoder<Unit>? {
        val blockEntity = dummyBlockEntities[block] ?: return null
        return DynamicOpsReadView.getReadDecoder(registries, blockEntity::loadWithComponents)
    }

    private fun <T : Entity> createDummyEntity(id: Identifier, entityType: EntityType<T>): Pair<EntityType<T>, Entity>? {
        try {
            @Suppress("UNCHECKED_CAST")
            val entity = (entityType as EntityTypeAccessor<T>).factory.create(entityType, dummyWorld) ?: return null
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

    enum class DataObjectSourceKind {
        ENTITY_SUMMON,
        ENTITY_CHANGE,
        BLOCK_ENTITY_CHANGE,
    }
}
