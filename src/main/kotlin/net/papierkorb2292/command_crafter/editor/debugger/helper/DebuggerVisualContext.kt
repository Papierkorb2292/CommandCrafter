package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.network.PacketByteBuf
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.networking.ByteBufWritable
import net.papierkorb2292.command_crafter.networking.readRange
import net.papierkorb2292.command_crafter.networking.writeRange
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.debug.Source

/**
 * Source paths should be in the format provided by [PackContentFileType.toStringPath]
 */
class DebuggerVisualContext(val source: Source, val range: Range): ByteBufWritable {
    constructor(buf: PacketByteBuf): this(
        buf.readSource(),
        buf.readRange()
    )

    override fun write(buf: PacketByteBuf) {
        buf.writeSource(source)
        buf.writeRange(range)
    }
}