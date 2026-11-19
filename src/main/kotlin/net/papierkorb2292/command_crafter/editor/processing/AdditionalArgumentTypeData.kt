package net.papierkorb2292.command_crafter.editor.processing

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding

object AdditionalArgumentTypeData {
    val dataTypeRegistryKey = ResourceKey.createRegistryKey<AdditionalDataType<*>>(Identifier.fromNamespaceAndPath("command_crafter", "additional_argument_data"))
    val dataTypeRegistry = FabricRegistryBuilder.create(dataTypeRegistryKey).buildAndRegister()

    lateinit var packContentFileType: AdditionalDataType<PackContentFileType>
        private set
    lateinit var dataObjectSource: AdditionalDataType<DataObjectDecoding.DataObjectSource>
        private set
    lateinit var nonPlayerSelector: AdditionalDataType<Boolean>
        private set

    fun registerTypes() {
        packContentFileType = registerAdditionalDataType(
            Identifier.fromNamespaceAndPath("command_crafter","pack_content_file_type"),
            PackContentFileType.PACKET_CODEC.cast()
        )
        dataObjectSource = registerAdditionalDataType(
            Identifier.fromNamespaceAndPath("command_crafter", "data_object_source"),
            DataObjectDecoding.DATA_OBJECT_SOURCE_PACKET_CODEC.cast()
        )
        nonPlayerSelector = registerAdditionalDataType(
            Identifier.fromNamespaceAndPath("command_crafter", "non_player_selector"),
            ByteBufCodecs.BOOL.cast()
        )
    }

    private fun <TData: Any> registerAdditionalDataType(id: Identifier, packetCodec: StreamCodec<FriendlyByteBuf, TData>): AdditionalDataType<TData> {
        return Registry.register(dataTypeRegistry, id, AdditionalDataType(id, packetCodec))
    }

    val ARGUMENT_DATA_STREAM_CODEC = ByteBufCodecs.registry(dataTypeRegistryKey).dispatch(
        AdditionalData<*>::type,
        AdditionalDataType<*>::fullCodec,
    ).mapStream<FriendlyByteBuf> {
        RegistryFriendlyByteBuf(it.unwrap(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))
    }

    class AdditionalData<T: Any>(
        val type: AdditionalDataType<T>,
        val value: T
    )

    class AdditionalDataType<T: Any>(val id: Identifier, packetCodec: StreamCodec<FriendlyByteBuf, T>) {
        val fullCodec = packetCodec.map({ data -> AdditionalData(this, data) }, AdditionalData<T>::value)

        override fun toString() = "<AdditionalDataType: ${id}>"
    }
}