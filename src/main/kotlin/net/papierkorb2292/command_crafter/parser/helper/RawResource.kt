package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.datafixers.util.Either
import net.minecraft.util.Identifier

class RawResource(val type: RawResourceType) {
    val content: MutableList<Either<String, RawResource>> = ArrayList()
    var id: Identifier? = null

    data class RawResourceType(val prefix: String, val fileExtension: String)

    companion object {
        val FUNCTION_TYPE = RawResourceType("functions", "mcfunction")
        val FUNCTION_TAG_TYPE = RawResourceType("tags/functions", "json")
    }
}