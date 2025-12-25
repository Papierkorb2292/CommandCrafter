package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.datafixers.util.Either
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType

class RawResource(val type: RawResourceType) {
    val content: MutableList<Either<String, RawResource>> = ArrayList()
    var id: Identifier? = null

    data class RawResourceType(val prefix: String, val fileExtension: String)

    companion object {
        val FUNCTION_TYPE = RawResourceType(PackContentFileType.FUNCTIONS_FILE_TYPE.contentTypePath, "mcfunction")
        val FUNCTION_TAG_TYPE = RawResourceType(PackContentFileType.FUNCTION_TAGS_FILE_TYPE.contentTypePath, "json")
    }
}