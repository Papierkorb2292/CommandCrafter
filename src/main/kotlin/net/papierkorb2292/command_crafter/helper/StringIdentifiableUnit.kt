package net.papierkorb2292.command_crafter.helper

import net.minecraft.util.StringRepresentable

enum class StringIdentifiableUnit : StringRepresentable {
    INSTANCE {
        override fun getSerializedName(): String {
            return "unit"
        }
    }
}