package net.papierkorb2292.command_crafter.editor.debugger

import net.papierkorb2292.command_crafter.editor.EditorClientFileFinder
import java.util.*
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
     * Finds a file matching the pattern (note that this shouldn't be used to search for folders, because VSCode's findFiles doesn't return them)
     *
     * This is done by repeatedly removing the first segment of the path until either a file is found or the path is empty.
     * This way, even if the pattern contains the workspace folder name, it will still be found after the workspace folder segment has
     * been removed from the pattern.
     */
    fun findFileWithWorkspace(pattern: String): CompletableFuture<String?> {
        val resultFuture = checkCacheForPattern(pattern).thenCompose { cached ->
            if(cached != null) CompletableFuture.completedFuture(cached)
            else requestPatternRecursive(pattern)
        }

        return resultFuture.thenApply {
            if(it != null)
                cache[pattern] = it
            it
        }
    }

    fun startBatch() = Batched()

    private fun requestPatternRecursive(pattern: String): CompletableFuture<String?> {
        if(pattern.isEmpty())
            return CompletableFuture.completedFuture(null)
        return fileFinder.findFiles(pattern).thenCompose { response ->
            if(response.isNotEmpty())
                CompletableFuture.completedFuture(response.first())
            else {
                val segmentEnd = pattern.indexOf('/')
                if(segmentEnd == -1)
                    CompletableFuture.completedFuture(null)
                else requestPatternRecursive(pattern.substring(segmentEnd + 1, pattern.length))
            }
        }
    }

    private fun checkCacheForPattern(pattern: String): CompletableFuture<String?> {
        val cached = cache[pattern] ?: return CompletableFuture.completedFuture(null)
        return fileFinder.fileExists(cached).thenApply {
            if(it) cached
            else {
                cache.remove(pattern)
                null
            }
        }
    }


    inner class Batched {
        val requests: MutableMap<String, CompletableFuture<String?>> = Collections.synchronizedMap(HashMap())

        fun findFileWithWorkspace(pattern: String): CompletableFuture<String?> =
            requests.computeIfAbsent(pattern, this@WorkspaceFileFinder::findFileWithWorkspace)
    }
}