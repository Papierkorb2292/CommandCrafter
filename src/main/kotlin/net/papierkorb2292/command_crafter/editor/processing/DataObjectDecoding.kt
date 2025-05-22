package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.CommandContext
import com.mojang.serialization.Codec
import com.mojang.serialization.Decoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.block.Block
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.BlockStateArgumentType
import net.minecraft.command.argument.RegistryEntryReferenceArgumentType
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.RegistryKeys
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer
import net.papierkorb2292.command_crafter.helper.DummyWorld
import net.papierkorb2292.command_crafter.helper.memoizeLast
import net.papierkorb2292.command_crafter.mixin.editor.processing.BlockEntityTypeAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.EntityTypeAccessor
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader

class DataObjectDecoding(private val registries: DynamicRegistryManager) {
    companion object {
        val GET_FOR_REGISTRIES = ::DataObjectDecoding.memoizeLast()

        private val DATA_OBJECT_SOURCE_PACKET_CODEC: PacketCodec<ByteBuf, DataObjectSource> = PacketCodec.tuple(
            enumConstantCodec(DataObjectSourceKind::class.java),
            DataObjectSource::kind,
            PacketCodecs.STRING,
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
                Identifier.of("command_crafter","data_object_source"),
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

    val dummyWorld = DummyWorld(registries, FeatureFlags.FEATURE_MANAGER.featureSet)

    val dummyEntityDecoder = registries.getOrThrow(RegistryKeys.ENTITY_TYPE).entrySet.asSequence()
        .mapNotNull { createDummyEntityDecoder(it.key.value, it.value) }
        .toMap()

    val dummyBlockEntityDecoders = registries.getOrThrow(RegistryKeys.BLOCK_ENTITY_TYPE).entrySet.asSequence()
        .mapNotNull { createDummyBlockEntityDecoder(it.key.value, it.value) }
        .flatten()
        .toMap()

    fun getDecoderForSource(dataObjectSource: DataObjectSource, context: CommandContext<CommandSource>): Decoder<Unit>? {
        return when(dataObjectSource.kind) {
            DataObjectSourceKind.ENTITY_REGISTRY_ENTRY -> {
                @Suppress("UNCHECKED_CAST")
                dummyEntityDecoder[RegistryEntryReferenceArgumentType.getEntityType(context as CommandContext<ServerCommandSource>, dataObjectSource.argumentName).value()]
            }
        }
    }

    private fun <T : Entity> createDummyEntityDecoder(id: Identifier, entityType: EntityType<T>): Pair<EntityType<T>, Decoder<Unit>>? {
        try {
            @Suppress("UNCHECKED_CAST")
            val entity = (entityType as EntityTypeAccessor<T>).factory.create(entityType, dummyWorld) ?: return null
            return entityType to DynamicOpsReadView.getReadDecoder(registries, entity::readData)
        } catch(e: Throwable) {
            CommandCrafter.LOGGER.warn("Error creating dummy entity of type $id", e)
            return null
        }
    }

    private fun <T : BlockEntity> createDummyBlockEntityDecoder(id: Identifier, blockEntityType: BlockEntityType<T>): Sequence<Pair<Block, Decoder<Unit>>>? {
        try {
            @Suppress("UNCHECKED_CAST")
            val accessor = blockEntityType as BlockEntityTypeAccessor<T>
            val blockEntity = accessor.factory.create(BlockPos.ORIGIN, accessor.blocks.first().defaultState) ?: return null
            val decoder = DynamicOpsReadView.getReadDecoder(registries, blockEntity::read)
            return accessor.blocks.asSequence().map { it to decoder }
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
