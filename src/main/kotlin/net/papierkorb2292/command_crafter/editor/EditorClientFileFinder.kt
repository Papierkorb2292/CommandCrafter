package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.editor.files.FileStat
import net.papierkorb2292.command_crafter.editor.files.FileType
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
     * Returns metadata for a file or null if the file wasn't found. The uri is in the format of [EditorURI] and must be absolute
     */
    fun statFile(uri: String): CompletableFuture<FileStat?>
}

fun EditorClientFileFinder.fileExistsOfType(uri: String, type: FileType): CompletableFuture<Boolean> {
    return statFile(uri).thenApply { stat ->
        stat != null && stat.type.hasFlag(type) // Use hasFlag in-case the file is a symbolic link
    }
}