package net.papierkorb2292.command_crafter.helper

import net.minecraft.util.StringRepresentable

class StringIdentifiableUnit(val name: String) : StringRepresentable {
    override fun getSerializedName() = name
}