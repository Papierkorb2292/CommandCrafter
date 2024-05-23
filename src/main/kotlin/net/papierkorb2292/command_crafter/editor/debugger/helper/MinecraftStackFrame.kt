package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.papierkorb2292.command_crafter.networking.SCOPE_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.array
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import org.eclipse.lsp4j.debug.Scope
import org.eclipse.lsp4j.debug.StackFramePresentationHint

class MinecraftStackFrame(
    val name: String,
    val visualContext: DebuggerVisualContext,
    val variableScopes: Array<Scope>,
    val presentationHint: StackFramePresentationHint = StackFramePresentationHint.NORMAL
) {
    companion object {
        val PRESENTATION_HINT_CODEC = enumConstantCodec(StackFramePresentationHint::class.java)
        val PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            MinecraftStackFrame::name,
            DebuggerVisualContext.PACKET_CODEC,
            MinecraftStackFrame::visualContext,
            SCOPE_PACKET_CODEC.array(),
            MinecraftStackFrame::variableScopes,
            PRESENTATION_HINT_CODEC,
            MinecraftStackFrame::presentationHint,
            ::MinecraftStackFrame
        )
    }
}