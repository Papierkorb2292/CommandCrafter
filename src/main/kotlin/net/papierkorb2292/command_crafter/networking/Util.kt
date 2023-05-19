package net.papierkorb2292.command_crafter.networking

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.network.PacketByteBuf

fun Packet.write(): PacketByteBuf = PacketByteBufs.create().also { write(it) }