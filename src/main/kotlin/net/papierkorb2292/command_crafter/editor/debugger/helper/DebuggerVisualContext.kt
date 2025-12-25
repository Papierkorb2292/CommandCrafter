package net.papierkorb2292.command_crafter.editor.debugger.helper

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.networking.RANGE_PACKET_CODEC
import net.papierkorb2292.command_crafter.networking.SOURCE_PACKET_CODEC
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.debug.Source

/**
 * Source paths should be in the format provided by [PackContentFileType.toStringPath]
 */
class DebuggerVisualContext(val source: Source, val range: Range) {
    companion object {
        val PACKET_CODEC: StreamCodec<ByteBuf, DebuggerVisualContext> = StreamCodec.composite(
            SOURCE_PACKET_CODEC,
            DebuggerVisualContext::source,
            RANGE_PACKET_CODEC,
            DebuggerVisualContext::range,
            ::DebuggerVisualContext
        )
    }
}