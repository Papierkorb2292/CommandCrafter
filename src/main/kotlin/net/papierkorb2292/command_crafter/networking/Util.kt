package net.papierkorb2292.command_crafter.networking

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.network.PacketByteBuf
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.util.*
import kotlin.experimental.and

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

fun PacketByteBuf.writeNullableVarInt(value: Int?) {
    if(value == null) {
        writeByte(0)
        return
    }
    var i: Int = value
    if(i and -64 == 0) {
        writeByte(i)
        return
    }
    writeByte(i and 63 or 128)
    i = i ushr 6
    while (i and -128 != 0) {
        writeByte(i and 127 or 128)
        i = i ushr 7
    }
    writeByte(i)
}

fun PacketByteBuf.readNullableVarInt(): Int? {
    val first = readByte()
    if(first == 0.toByte()) {
        return null
    }
    var result = first.toInt() and 63
    var shift = 6
    while(first and 128.toByte() != 0.toByte()) {
        val next = readByte()
        result = result or ((next and 127).toInt() shl shift)
        shift += 7
    }
    return result
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