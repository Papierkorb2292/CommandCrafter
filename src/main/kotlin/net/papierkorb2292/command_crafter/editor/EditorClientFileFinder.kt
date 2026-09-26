package net.papierkorb2292.command_crafter.editor

import java.util.concurrent.CompletableFuture

interface EditorClientFileFinder {
    /**
     * Searches the workspace for files matching the given glob pattern. Pattern must be relative to the workspace folder(s) and use '/' as separator
     */
    fun findFiles(pattern: String): CompletableFuture<Array<String>>

    /**
     * Searches the file system for files matching the given pattern. Pattern must be relative to the given baseUri and use '/' as separator.
     * The search is not limited to the workspace folder(s) and may access any file on the system that the editor has access to. The baseUri is in the format of [EditorURI]
     */
    fun findFilesRelative(params: FindFilesRelativeParams): CompletableFuture<Array<String>>

    /**
     * Returns whether a file exists. The uri is in the format of [EditorURI]
     */
    fun fileExists(uri: String): CompletableFuture<Boolean>
}