package net.papierkorb2292.command_crafter.parser.helper

import net.minecraft.nbt.CompoundTag

interface NbtPathFilteredRootNodeFilterProvider {
    fun `command_crafter$getFilter`(): CompoundTag
}