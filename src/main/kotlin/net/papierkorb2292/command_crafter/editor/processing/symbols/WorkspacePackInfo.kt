package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.EditorURI
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType.Companion.types
import java.util.concurrent.CompletableFuture

class WorkspacePackInfo(
    val baseUri: EditorURI,
    val packFolderName: String,
    var packType: PackContentFileType.PackType?
) {
    companion object {
        fun forFolder(folderLocation: EditorURI, languageServer: MinecraftLanguageServer): CompletableFuture<WorkspacePackInfo> {
            return findType(folderLocation, languageServer).thenApply { packType ->
                WorkspacePackInfo(folderLocation, folderLocation.getFileName(), packType)
            }
        }

        fun findType(folderLocation: EditorURI, languageServer: MinecraftLanguageServer): CompletableFuture<PackContentFileType.PackType?> {
            val dataPath = folderLocation.resolve("data").toString()
            val assetsPath = folderLocation.resolve("assets").toString()
            return languageServer.client!!.fileExists(dataPath)
                .thenCombine(languageServer.client!!.fileExists(assetsPath)) { dataFolderExists, assetsFolderExists ->
                    if(dataFolderExists && assetsFolderExists) {
                        null
                    } else if(dataFolderExists) {
                        PackContentFileType.PackType.DATA
                    } else if(assetsFolderExists) {
                        PackContentFileType.PackType.RESOURCE
                    } else {
                        null
                    }
                }
        }
    }

    fun updatePackType(languageServer: MinecraftLanguageServer): CompletableFuture<Void> {
        return findType(baseUri, languageServer).thenAccept { packType ->
            this.packType = packType
        }
    }

    fun idFromUri(uri: EditorURI): TypedId? {
        val relativePath = baseUri.relativePath(uri) ?: return null
        val segments = relativePath.split('/')
        if(segments.size < 3) return null
        val packType = PackContentFileType.packTypeFolders[segments[0]] ?: return null
        val namespace = segments[1]

        for(i in segments.size - 1 downTo 3) {
            val potentialContentTypePath = segments.subList(2, i).joinToString("/")
            val type = types[potentialContentTypePath] ?: continue
            if(type.packType != packType) continue
            val remainingPath = segments.subList(i, segments.size).joinToString("/")
            val resourceId = Identifier.tryBuild(namespace, remainingPath)
            if(resourceId != null)
                return TypedId(resourceId, type)
        }
        return null
    }

    data class TypedId(val id: Identifier, val type: PackContentFileType)
}