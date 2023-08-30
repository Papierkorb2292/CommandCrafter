package net.papierkorb2292.command_crafter.networking

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.network.PacketByteBuf
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.util.*

fun ByteBufWritable.write(): PacketByteBuf = PacketByteBufs.create().also { write(it) }

fun PacketByteBuf.writeNullableString(value: String?) {
    if(value == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeString(value)
    }
}
fun PacketByteBuf.readNullableString(): String? {
    return if(readBoolean()) {
        readString()
    } else {
        null
    }
}

fun PacketByteBuf.writeNullableInt(value: Int?) {
    if(value == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeInt(value)
    }
}
fun PacketByteBuf.readNullableInt(): Int? {
    return if(readBoolean()) {
        readInt()
    } else {
        null
    }
}

fun PacketByteBuf.writeNullableUUID(uuid: UUID?) {
    if(uuid == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeUuid(uuid)
    }
}

fun PacketByteBuf.readNullableUUID(): UUID? {
    return if(readBoolean()) {
        readUuid()
    } else {
        null
    }
}

fun PacketByteBuf.writeNullableBool(value: Boolean?) {
    if(value == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeBoolean(value)
    }
}

fun PacketByteBuf.readNullableBool(): Boolean? {
    return if(readBoolean()) {
        readBoolean()
    } else null
}

fun PacketByteBuf.writeNullableEnumConstant(value: Enum<*>?) {
    if(value == null) {
        writeBoolean(false)
    } else {
        writeBoolean(true)
        writeEnumConstant(value)
    }
}

fun <T : Enum<T>> PacketByteBuf.readNullableEnumConstant(enumClass: Class<T>): T? {
    return if(readBoolean()) {
        readEnumConstant(enumClass)
    } else null
}

fun PacketByteBuf.writeRange(range: Range) {
    writeVarInt(range.start.line)
    writeVarInt(range.start.character)
    writeVarInt(range.end.line)
    writeVarInt(range.end.character)
}

fun PacketByteBuf.readRange()
        = Range(Position(readVarInt(), readVarInt()), Position(readVarInt(), readVarInt()))