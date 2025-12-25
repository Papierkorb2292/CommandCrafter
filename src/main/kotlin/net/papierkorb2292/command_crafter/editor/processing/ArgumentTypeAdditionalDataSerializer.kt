package net.papierkorb2292.command_crafter.editor.processing

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.serialization.Encoder
import com.mojang.serialization.JsonOps
import io.netty.handler.codec.DecoderException
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.synchronization.ArgumentTypeInfo
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.helper.getOrNull

class ArgumentTypeAdditionalDataSerializer<A: ArgumentType<*>>(
    val delegate: ArgumentTypeInfo<A, ArgumentTypeInfo.Template<A>>
) : ArgumentTypeInfo<A, ArgumentTypeInfo.Template<A>> {
    companion object {
        private val additionalDataTypes = mutableMapOf<Identifier, AdditionalDataType<*>>()
        val shouldWriteAdditionalDataTypes = ThreadLocal<Boolean>()
        const val ADDITIONAL_DATA_KEY = "command_crafter:additional_data"

        fun <TData: Any> registerAdditionalDataType(
            id: Identifier,
            getter: (ArgumentType<*>) -> TData?,
            setter: (ArgumentType<*>, TData) -> Boolean,
            packetCodec: StreamCodec<FriendlyByteBuf, TData>,
            encoder: Encoder<TData>
        ) {
            additionalDataTypes[id] = AdditionalDataType(getter, setter, packetCodec, encoder)
        }
    }

    override fun serializeToNetwork(properties: ArgumentTypeInfo.Template<A>, buf: FriendlyByteBuf) {
        if(properties is WrappedArgumentTypeProperties) {
            delegate.serializeToNetwork(properties.delegate, buf)
            if(shouldWriteAdditionalDataTypes.getOrNull() == true && properties.additionalData.isNotEmpty()) {
                buf.writeUtf(ADDITIONAL_DATA_KEY)
                buf.writeVarInt(properties.additionalData.size)
                for((id, data) in properties.additionalData) {
                    buf.writeIdentifier(id)
                    additionalDataTypes.getValue(id).uncheckedWriteDataToPacket(buf, data)
                }
            }
        } else {
            delegate.serializeToNetwork(properties, buf)
        }
    }

    override fun deserializeFromNetwork(buf: FriendlyByteBuf): ArgumentTypeInfo.Template<A> {
        val baseProperties = delegate.deserializeFromNetwork(buf)
        buf.markReaderIndex()
        try {
            if(buf.readUtf() == ADDITIONAL_DATA_KEY) {
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

    override fun unpack(argumentType: A): ArgumentTypeInfo.Template<A> {
        val delegateProperties = delegate.unpack(argumentType)
        val additionalData = additionalDataTypes.mapValues { it.value.getter(argumentType) }.filterValues { it != null }
        return WrappedArgumentTypeProperties(delegateProperties, additionalData)
    }

    override fun serializeToJson(properties: ArgumentTypeInfo.Template<A>, json: JsonObject) {
        if(properties !is WrappedArgumentTypeProperties) {
            delegate.serializeToJson(properties, json)
            return
        }
        delegate.serializeToJson(properties.delegate, json)
        val serializedData = JsonObject()
        for((id, data) in properties.additionalData) {
            serializedData.add(id.toString(), additionalDataTypes.getValue(id).uncheckedWriteDataToJson(data))
        }
        json.add(ADDITIONAL_DATA_KEY, serializedData)
    }

    class AdditionalDataType<T: Any>(
        val getter: (ArgumentType<*>) -> T?,
        val setter: (ArgumentType<*>, T) -> Boolean,
        val packetCodec: StreamCodec<FriendlyByteBuf, T>,
        val encoder: Encoder<T>,
    ) {
        fun uncheckedTryAddDataToArgumentType(argument: ArgumentType<*>, data: Any?) {
            @Suppress("UNCHECKED_CAST")
            if(!setter(argument, data as T))
                CommandCrafter.LOGGER.warn("Failed to set additional data '$data' for argument type '$argument'")
        }

        fun uncheckedWriteDataToPacket(buf: FriendlyByteBuf, data: Any?) {
            @Suppress("UNCHECKED_CAST")
            packetCodec.encode(buf, data as T)
        }

        fun uncheckedWriteDataToJson(data: Any?): JsonElement {
            @Suppress("UNCHECKED_CAST")
            return encoder.encodeStart(JsonOps.INSTANCE, data as T).orThrow
        }
    }

    private inner class WrappedArgumentTypeProperties(val delegate: ArgumentTypeInfo.Template<A>, val additionalData: Map<Identifier, Any?>): ArgumentTypeInfo.Template<A> {
        override fun instantiate(commandRegistryAccess: CommandBuildContext): A {
            val argumentType = delegate.instantiate(commandRegistryAccess)
            for((id, data) in additionalData) {
                additionalDataTypes.getValue(id).uncheckedTryAddDataToArgumentType(argumentType, data)
            }
            return argumentType
        }

        override fun type(): ArgumentTypeInfo<A, *> {
            return this@ArgumentTypeAdditionalDataSerializer
        }
    }
}