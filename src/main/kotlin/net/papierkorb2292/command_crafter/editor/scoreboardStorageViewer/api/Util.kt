package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.papierkorb2292.command_crafter.networking.array
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent

val FILE_EVENT_PACKET_CODEC: PacketCodec<ByteBuf, FileEvent> = PacketCodec.tuple(
    PacketCodecs.STRING,
    FileEvent::getUri,
    PacketCodecs.INTEGER,
    { it.type.value },
    { uri, type -> FileEvent(uri, FileChangeType.forValue(type)) }
)

val FILE_EVENT_ARRAY_PACKET_CODEC = FILE_EVENT_PACKET_CODEC.array()