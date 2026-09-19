package net.papierkorb2292.command_crafter.editor

import java.util.concurrent.CompletableFuture

interface EditorClientFileFinder {
    /**
     * Searches the workspace for files matching the given pattern
     */
    fun findFiles(pattern: String): CompletableFuture<Array<String>>

    /**
     * Returns whether a file exists. The uri is in the format of [EditorURI]
     */
    fun fileExists(uri: String): CompletableFuture<Boolean>
}