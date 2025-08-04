package net.papierkorb2292.command_crafter.editor.debugger

import net.papierkorb2292.command_crafter.editor.EditorClientFileFinder
import java.util.Collections
import java.util.concurrent.CompletableFuture

/**
 * Searches for files using the [EditorClientFileFinder].
 *
 * This class handles the cases in which the pattern includes the workspace directory or its parents, which VSCode's 'findFiles' wouldn't normally find.
 * Because this search takes longer, results are also cached.
 */
class WorkspaceFileFinder(private val fileFinder: EditorClientFileFinder) {
    private val cache: MutableMap<String, String> = Collections.synchronizedMap(HashMap())

    /**
     * Finds a file matching the pattern.
     *
     * This is done by repeatedly removing the first segment of the path until either a file is found or the path is empty.
     * This way, even if the pattern contains the workspace folder name, it will still be found after the workspace folder segment has
     * been removed from the pattern.
     */
    fun findFileWithWorkspace(pattern: String): CompletableFuture<String?> {
        var trimmedPattern: CharSequence = pattern
        var future = checkCacheForPattern(pattern)

        while(trimmedPattern.isNotEmpty()) {
            val finalPattern = trimmedPattern
            future = future.thenCompose { prev ->
                if(prev != null) CompletableFuture.completedFuture(prev)
                else fileFinder.findFiles(finalPattern.toString()).thenApply { it.firstOrNull() }
            }
            val segmentEnd = trimmedPattern.indexOf('/')
            if(segmentEnd == -1)
                break
            trimmedPattern = trimmedPattern.subSequence(segmentEnd + 1, trimmedPattern.length)
        }

        return future.thenApply {
            if(it != null)
                cache[pattern] = it
            it
        }
    }

    private fun checkCacheForPattern(pattern: String): CompletableFuture<String?> {
        val cached = cache[pattern] ?: return CompletableFuture.completedFuture(null)
        return fileFinder.fileExists(cached).thenApply { if(it) cached else null }
    }
}