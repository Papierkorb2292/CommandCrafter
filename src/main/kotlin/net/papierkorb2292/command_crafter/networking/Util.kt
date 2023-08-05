package net.papierkorb2292.command_crafter.networking

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.network.PacketByteBuf

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