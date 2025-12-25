package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.CommandContext
import com.mojang.serialization.Codec
import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.blocks.BlockStateArgument
import net.minecraft.commands.arguments.ResourceArgument
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.commands.CommandSourceStack
import net.minecraft.resources.Identifier
import net.minecraft.core.BlockPos
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer
import net.papierkorb2292.command_crafter.helper.DummyWorld
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.mixin.editor.processing.BlockEntityTypeAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.EntityTypeAccessor
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class DataObjectDecoding(private val registries: RegistryAccess) {
    companion object {
        val GET_FOR_REGISTRIES = ::DataObjectDecoding.memoizeLast()

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
            val languageServer = directiveStringReader.resourceCreator.languageServer ?: return null
            return GET_FOR_REGISTRIES(languageServer.dynamicRegistryManager)
        }
    }

    val dummyWorld = DummyWorld(registries, FeatureFlags.REGISTRY.allFlags())

    val dummyEntityDecoder = registries.lookupOrThrow(Registries.ENTITY_TYPE).entrySet().asSequence()
        .mapNotNull { createDummyEntityDecoder(it.key.identifier(), it.value) }
        .toMap()

    val dummyBlockEntityDecoders = registries.lookupOrThrow(Registries.BLOCK_ENTITY_TYPE).entrySet().asSequence()
        .mapNotNull { createDummyBlockEntityDecoder(it.key.identifier(), it.value) }
        .flatten()
        .toMap()

    fun getDecoderForSource(dataObjectSource: DataObjectSource, context: CommandContext<SharedSuggestionProvider>): Decoder<Unit>? {
        return try {
            when(dataObjectSource.kind) {
                DataObjectSourceKind.ENTITY_REGISTRY_ENTRY -> {
                    @Suppress("UNCHECKED_CAST")
                    dummyEntityDecoder[ResourceArgument.getEntityType(
                        context as CommandContext<CommandSourceStack>,
                        dataObjectSource.argumentName
                    ).value()]
                }
            }
        } catch(_: IllegalArgumentException) {
            //TODO: This can happen when accessing an argument that contains a macro variable. Maybe this case should be handled by trying out all possible values?
            null
        }
    }

    private fun <T : Entity> createDummyEntityDecoder(id: Identifier, entityType: EntityType<T>): Pair<EntityType<T>, Decoder<Unit>>? {
        try {
            @Suppress("UNCHECKED_CAST")
            val entity = (entityType as EntityTypeAccessor<T>).factory.create(entityType, dummyWorld) ?: return null
            return entityType to DynamicOpsReadView.getReadDecoder(registries, entity::load)
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.warn("Error creating dummy entity of type $id", e)
            return null
        }
    }

    private fun <T : BlockEntity> createDummyBlockEntityDecoder(id: Identifier, blockEntityType: BlockEntityType<T>): Sequence<Pair<Block, Decoder<Unit>>>? {
        try {
            @Suppress("UNCHECKED_CAST")
            val accessor = blockEntityType as BlockEntityTypeAccessor<T>
            val blockEntity = accessor.factory.create(BlockPos.ZERO, accessor.validBlocks.first().defaultBlockState()) ?: return null
            val decoder = DynamicOpsReadView.getReadDecoder(registries, blockEntity::loadWithComponents)
            return accessor.validBlocks.asSequence().map { it to decoder }
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.warn("Error creating dummy block entity of type $id", e)
            return null
        }
    }

    data class DataObjectSource(val kind: DataObjectSourceKind, val argumentName: String)

    enum class DataObjectSourceKind {
        ENTITY_REGISTRY_ENTRY
    }
}
