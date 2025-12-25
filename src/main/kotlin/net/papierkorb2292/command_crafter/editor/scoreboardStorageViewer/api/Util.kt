package net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.papierkorb2292.command_crafter.networking.array

val FILE_EVENT_PACKET_CODEC: StreamCodec<ByteBuf, FileEvent> = StreamCodec.composite(
    ByteBufCodecs.STRING_UTF8,
    FileEvent::uri,
    ByteBufCodecs.INT,
    { it.type.value },
    { uri, type -> FileEvent(uri, FileChangeType.fromInt(type)) }
)

val FILE_EVENT_ARRAY_PACKET_CODEC = FILE_EVENT_PACKET_CODEC.array()