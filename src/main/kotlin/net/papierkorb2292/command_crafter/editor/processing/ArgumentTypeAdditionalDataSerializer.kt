package net.papierkorb2292.command_crafter.editor.processing

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.serialization.Encoder
import com.mojang.serialization.JsonOps
import io.netty.handler.codec.DecoderException
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.serialize.ArgumentSerializer
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.helper.getOrNull

class ArgumentTypeAdditionalDataSerializer<A: ArgumentType<*>>(
    val delegate: ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>>
) : ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> {
    companion object {
        private val additionalDataTypes = mutableMapOf<Identifier, AdditionalDataType<*>>()
        val shouldWriteAdditionalDataTypes = ThreadLocal<Boolean>()
        const val ADDITIONAL_DATA_KEY = "command_crafter:additional_data"

        fun <TData> registerAdditionalDataType(
            id: Identifier,
            getter: (ArgumentType<*>) -> TData?,
            setter: (ArgumentType<*>, TData) -> Boolean,
            packetCodec: PacketCodec<PacketByteBuf, TData>,
            encoder: Encoder<TData>
        ) {
            additionalDataTypes[id] = AdditionalDataType(getter, setter, packetCodec, encoder)
        }
    }

    override fun writePacket(properties: ArgumentSerializer.ArgumentTypeProperties<A>, buf: PacketByteBuf) {
        if(properties is WrappedArgumentTypeProperties) {
            delegate.writePacket(properties.delegate, buf)
            if(shouldWriteAdditionalDataTypes.getOrNull() == true && properties.additionalData.isNotEmpty()) {
                buf.writeString(ADDITIONAL_DATA_KEY)
                buf.writeVarInt(properties.additionalData.size)
                for((id, data) in properties.additionalData) {
                    buf.writeIdentifier(id)
                    additionalDataTypes.getValue(id).uncheckedWriteDataToPacket(buf, data)
                }
            }
        } else {
            delegate.writePacket(properties, buf)
        }
    }

    override fun fromPacket(buf: PacketByteBuf): ArgumentSerializer.ArgumentTypeProperties<A> {
        val baseProperties = delegate.fromPacket(buf)
        buf.markReaderIndex()
        try {
            if(buf.readString() == ADDITIONAL_DATA_KEY) {
                val additionalData = mutableMapOf<Identifier, Any?>()
                for(i in 0 until buf.readVarInt()) {
                    val id = buf.readIdentifier()
                    val additionalDataType = additionalDataTypes[id]
                    if(additionalDataType == null) {
                        CommandCrafter.LOGGER.error("Received unknown additional data type '$id' for argument type properties '$baseProperties'")
                        // Can't continue reading additional data, since there's no way to know how many bytes belong to the unknown type
                        break
                    }
                    val data = additionalDataType.packetCodec.decode(buf)
                    additionalData[id] = data
                }
                return WrappedArgumentTypeProperties(baseProperties, additionalData)
            } else {
                buf.resetReaderIndex()
            }
        } catch (e: DecoderException) {
            buf.resetReaderIndex()
        }
        return WrappedArgumentTypeProperties(baseProperties, emptyMap())
    }

    override fun getArgumentTypeProperties(argumentType: A): ArgumentSerializer.ArgumentTypeProperties<A> {
        val delegateProperties = delegate.getArgumentTypeProperties(argumentType)
        val additionalData = additionalDataTypes.mapValues { it.value.getter(argumentType) }.filterValues { it != null }
        return WrappedArgumentTypeProperties(delegateProperties, additionalData)
    }

    override fun writeJson(properties: ArgumentSerializer.ArgumentTypeProperties<A>, json: JsonObject) {
        if(properties !is WrappedArgumentTypeProperties) {
            delegate.writeJson(properties, json)
            return
        }
        delegate.writeJson(properties.delegate, json)
        val serializedData = JsonObject()
        for((id, data) in properties.additionalData) {
            serializedData.add(id.toString(), additionalDataTypes.getValue(id).uncheckedWriteDataToJson(data))
        }
        json.add(ADDITIONAL_DATA_KEY, serializedData)
    }

    class AdditionalDataType<T>(
        val getter: (ArgumentType<*>) -> T?,
        val setter: (ArgumentType<*>, T) -> Boolean,
        val packetCodec: PacketCodec<PacketByteBuf, T>,
        val encoder: Encoder<T>,
    ) {
        fun uncheckedTryAddDataToArgumentType(argument: ArgumentType<*>, data: Any?) {
            @Suppress("UNCHECKED_CAST")
            if(!setter(argument, data as T))
                CommandCrafter.LOGGER.warn("Failed to set additional data '$data' for argument type '$argument'")
        }

        fun uncheckedWriteDataToPacket(buf: PacketByteBuf, data: Any?) {
            @Suppress("UNCHECKED_CAST")
            packetCodec.encode(buf, data as T)
        }

        fun uncheckedWriteDataToJson(data: Any?): JsonElement {
            @Suppress("UNCHECKED_CAST")
            return encoder.encodeStart(JsonOps.INSTANCE, data as T).orThrow
        }
    }

    private inner class WrappedArgumentTypeProperties(val delegate: ArgumentSerializer.ArgumentTypeProperties<A>, val additionalData: Map<Identifier, Any?>): ArgumentSerializer.ArgumentTypeProperties<A> {
        override fun createType(commandRegistryAccess: CommandRegistryAccess?): A {
            val argumentType = delegate.createType(commandRegistryAccess)
            for((id, data) in additionalData) {
                additionalDataTypes.getValue(id).uncheckedTryAddDataToArgumentType(argumentType, data)
            }
            return argumentType
        }

        override fun getSerializer(): ArgumentSerializer<A, *> {
            return this@ArgumentTypeAdditionalDataSerializer
        }
    }
}