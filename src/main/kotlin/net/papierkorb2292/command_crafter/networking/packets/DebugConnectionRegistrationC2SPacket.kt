package net.papierkorb2292.command_crafter.networking.packets

import io.netty.buffer.ByteBuf
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.core.UUIDUtil
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection.Companion.DEBUG_TARGET_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.optional
import net.papierkorb2292.command_crafter.networking.toOptional
import java.util.*

class DebugConnectionRegistrationC2SPacket(val oneTimeDebugTarget: EditorDebugConnection.DebugTarget?, val nextSourceReference: Int, val suspendServer: Boolean, val debugConnectionId: UUID):
    CustomPacketPayload {
    companion object {
        val ID = CustomPacketPayload.Type<DebugConnectionRegistrationC2SPacket>(Identifier.fromNamespaceAndPath("command_crafter", "editor_debug_connection_registration"))
        val CODEC: StreamCodec<ByteBuf, DebugConnectionRegistrationC2SPacket> = StreamCodec.composite(
            DEBUG_TARGET_PACKET_CODEC.optional(),
            DebugConnectionRegistrationC2SPacket::oneTimeDebugTarget.toOptional(),
            ByteBufCodecs.VAR_INT,
            DebugConnectionRegistrationC2SPacket::nextSourceReference,
            ByteBufCodecs.BOOL,
            DebugConnectionRegistrationC2SPacket::suspendServer,
            UUIDUtil.STREAM_CODEC,
            DebugConnectionRegistrationC2SPacket::debugConnectionId,
        ) { oneTimeDebugTarget, nextSourceReference, suspendServer, debugConnectionId ->
            DebugConnectionRegistrationC2SPacket(
                oneTimeDebugTarget.orElse(null),
                nextSourceReference,
                suspendServer,
                debugConnectionId
            )
        }
        val TYPE: CustomPacketPayload.TypeAndCodec<in RegistryFriendlyByteBuf, DebugConnectionRegistrationC2SPacket> =
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
    }

    override fun type() = ID
}