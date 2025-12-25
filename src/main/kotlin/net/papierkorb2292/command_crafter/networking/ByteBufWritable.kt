package net.papierkorb2292.command_crafter.networking

import net.minecraft.network.FriendlyByteBuf

interface ByteBufWritable {
    fun write(buf: FriendlyByteBuf)
}