package net.papierkorb2292.command_crafter.helper

import net.minecraft.util.StringIdentifiable

enum class StringIdentifiableUnit : StringIdentifiable {
    INSTANCE {
        override fun asString(): String {
            return "unit"
        }
    }
}