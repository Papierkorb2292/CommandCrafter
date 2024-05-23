package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.nbt.NbtCompound

interface NbtPathFilteredRootNodeFilterProvider {
    fun `command_crafter$getFilter`(): NbtCompound
}