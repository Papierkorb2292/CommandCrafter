package net.papierkorb2292.command_crafter.editor.processing.symbols

import net.papierkorb2292.command_crafter.editor.EditorURI
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import java.util.concurrent.CompletableFuture

class WorkspacePackInfo(
    val baseUri: EditorURI,
    val packType: CompletableFuture<PackContentFileType.PackType?>
) {
    companion object {
        fun forFolder(folderLocation: EditorURI, languageServer: MinecraftLanguageServer): WorkspacePackInfo {
            val dataPath = folderLocation.resolve("data").toString()
            val assetsPath = folderLocation.resolve("assets").toString()
            val packType = languageServer.client!!.fileExists(dataPath)
                .thenCombine(languageServer.client!!.fileExists(assetsPath)) { dataFolderExists, assetsFolderExists ->
                    if (dataFolderExists && assetsFolderExists) {
                        null
                    } else if (dataFolderExists) {
                        PackContentFileType.PackType.DATA
                    } else if (assetsFolderExists) {
                        PackContentFileType.PackType.RESOURCE
                    } else {
                        null
                    }
                }
            return WorkspacePackInfo(folderLocation, packType)
        }
    }
}