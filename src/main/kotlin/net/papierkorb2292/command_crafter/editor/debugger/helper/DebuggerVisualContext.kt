package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.networking.ByteBufWritable
import net.papierkorb2292.command_crafter.networking.readRange
import net.papierkorb2292.command_crafter.networking.writeRange
import org.eclipse.lsp4j.Range

class DebuggerVisualContext(val fileType: PackContentFileType, val fileId: Identifier, val range: Range, val frameContext: NbtCompound?): ByteBufWritable {
    constructor(buf: PacketByteBuf): this(
        PackContentFileType(buf),
        buf.readIdentifier(),
        buf.readRange(),
        buf.readNbt()
    )

    override fun write(buf: PacketByteBuf) {
        fileType.writeToBuf(buf)
        buf.writeIdentifier(fileId)
        buf.writeRange(range)
        buf.writeNbt(frameContext)
    }
}