package net.papierkorb2292.command_crafter.networking

import net.minecraft.network.PacketByteBuf

interface Packet {
    fun write(buf: PacketByteBuf)
}