package net.papierkorb2292.command_crafter.editor

import java.util.concurrent.CompletableFuture

interface EditorClientFileFinder {
    /**
     * Searches the workspace for files matching the given pattern
     */
    fun findFiles(pattern: String): CompletableFuture<Array<String>>

    fun fileExists(url: String): CompletableFuture<Boolean>
}