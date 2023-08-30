package net.papierkorb2292.command_crafter.editor.debugger.client

import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.network.PacketByteBuf
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.readNullableValueFormat
import net.papierkorb2292.command_crafter.editor.debugger.helper.writeNullableValueFormat
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.networking.*
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture

class NetworkVariablesReferencer(val packetSender: PacketSender, val pauseId: UUID) :
    VariablesReferencer {
    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        val requestId = UUID.randomUUID()
        val future = CompletableFuture<Array<Variable>>()
        NetworkServerConnection.currentGetVariablesRequests[requestId] = future
        packetSender.sendPacket(
            NetworkServerConnection.getVariablesRequestPacketChannel,
            GetVariablesRequestC2SPacket(pauseId, requestId, args).write()
        )
        return future
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val requestId = UUID.randomUUID()
        val future = CompletableFuture<VariablesReferencer.SetVariableResult?>()
        NetworkServerConnection.currentSetVariableRequests[requestId] = future
        packetSender.sendPacket(
            NetworkServerConnection.setVariableRequestPacketChannel,
            SetVariableRequestC2SPacket(pauseId, requestId, args).write()
        )
        return future
    }

    class GetVariablesRequestC2SPacket(val pauseId: UUID, val requestId: UUID, val args: VariablesArguments) : ByteBufWritable {
        constructor(buf: PacketByteBuf) : this(
            buf.readUuid(),
            buf.readUuid(),
            VariablesArguments().apply {
                variablesReference = buf.readVarInt()
                filter = buf.readNullableEnumConstant(VariablesArgumentsFilter::class.java)
                start = buf.readNullableInt()
                count = buf.readNullableInt()
                format = buf.readNullableValueFormat()
            }
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(pauseId)
            buf.writeUuid(requestId)
            buf.writeVarInt(args.variablesReference)
            buf.writeNullableEnumConstant(args.filter)
            buf.writeNullableInt(args.start)
            buf.writeNullableInt(args.count)
            buf.writeNullableValueFormat(args.format)
        }
    }

    class GetVariablesResponseS2CPacket(val requestId: UUID, val variables: Array<Variable>): ByteBufWritable {
        constructor(buf: PacketByteBuf) : this(
            buf.readUuid(),
            Array(buf.readVarInt()) {
                Variable().apply {
                    name = buf.readString()
                    value = buf.readString()
                    type = buf.readNullableString()
                    presentationHint = if(buf.readBoolean()) {
                        VariablePresentationHint().apply {
                            kind = buf.readNullableString()
                            attributes = if(buf.readBoolean()) {
                                Array(buf.readVarInt()) {
                                    buf.readString()
                                }
                            } else null
                            visibility = buf.readNullableString()
                            lazy = buf.readNullableBool()
                        }
                    } else null
                    evaluateName = buf.readNullableString()
                    variablesReference = buf.readVarInt()
                    namedVariables = buf.readNullableInt()
                    indexedVariables = buf.readNullableInt()
                    memoryReference = buf.readNullableString()
                }
            }
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(requestId)
            buf.writeVarInt(variables.size)
            for(it in variables) {
                buf.writeString(it.name)
                buf.writeString(it.value)
                buf.writeNullableString(it.type)
                val presentationHint = it.presentationHint
                if(presentationHint == null) {
                    buf.writeBoolean(false)
                } else {
                    buf.writeBoolean(true)
                    buf.writeNullableString(presentationHint.kind)
                    val attributes = presentationHint.attributes
                    if(attributes == null) {
                        buf.writeBoolean(false)
                    } else {
                        buf.writeBoolean(true)
                        buf.writeVarInt(attributes.size)
                        for(attribute in attributes) {
                            buf.writeString(attribute)
                        }
                    }
                    buf.writeNullableString(presentationHint.visibility)
                    buf.writeNullableBool(presentationHint.lazy)
                }
                buf.writeNullableString(it.evaluateName)
                buf.writeVarInt(it.variablesReference)
                buf.writeNullableInt(it.namedVariables)
                buf.writeNullableInt(it.indexedVariables)
                buf.writeNullableString(it.memoryReference)
            }
        }
    }

    class SetVariableRequestC2SPacket(val pauseId: UUID, val requestId: UUID, val args: SetVariableArguments) : ByteBufWritable {
        constructor(buf: PacketByteBuf) : this(
            buf.readUuid(),
            buf.readUuid(),
            SetVariableArguments().apply {
                variablesReference = buf.readVarInt()
                name = buf.readString()
                value = buf.readString()
                format = buf.readNullableValueFormat()
            }
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(pauseId)
            buf.writeUuid(requestId)
            buf.writeVarInt(args.variablesReference)
            buf.writeString(args.name)
            buf.writeString(args.value)
            buf.writeNullableValueFormat(args.format)
        }
    }

    class SetVariableResponseS2CPacket(val requestId: UUID, val response: VariablesReferencer.SetVariableResult?): ByteBufWritable {
        constructor(buf: PacketByteBuf) : this(
            buf.readUuid(),
            if(buf.readBoolean()) VariablesReferencer.SetVariableResult(buf) else null
        )

        override fun write(buf: PacketByteBuf) {
            buf.writeUuid(requestId)
            val response = response
            if(response == null) {
                buf.writeBoolean(false)
            } else {
                buf.writeBoolean(true)
                response.write(buf)
            }
        }
    }
}
