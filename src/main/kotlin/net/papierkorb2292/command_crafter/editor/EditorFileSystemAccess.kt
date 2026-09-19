package net.papierkorb2292.command_crafter.editor

import java.util.concurrent.CompletableFuture

interface EditorFileSystemAccess : EditorClientFileFinder {
    /**
     * Retrieves the content of a file. The uri is in the format of [EditorURI]
     */
    fun getFileContent(uri: String): CompletableFuture<String>
}