package net.papierkorb2292.command_crafter.editor

import it.unimi.dsi.fastutil.chars.CharSet
import net.papierkorb2292.command_crafter.helper.ensurePrefixed
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists

class DirectFileSystemAccess(workspaceRoots: List<Path>) : EditorFileSystemAccess {
    private val absRoots = workspaceRoots.map { it.toAbsolutePath() }

    override fun findFiles(pattern: String): CompletableFuture<Array<String>> =
        findFiles(pattern, absRoots)

    override fun findFilesRelative(params: FindFilesRelativeParams): CompletableFuture<Array<String>> {
        val parsedUri = EditorURI.parseURI(params.baseUri)
        if(parsedUri.scheme != "file")
            return CompletableFuture.completedFuture(arrayOf())
        val path = parsedUri.parseLocalPath()
            ?: return CompletableFuture.completedFuture(arrayOf())
        return findFiles(params.pattern, listOf(path))
    }

    // Note that Java doesn't interpret glob patterns the same as VSCode. In Java, `**` always
    // matches zero or more of any characters, including path separators, whereas VSCode seems to only
    // treat `**` like that if there's a `/` directly in front of it and after it (or the string ends there). Otherwise, it behaves like `*`.
    // Additionally, a `**` segment for VSCode is also allowed to match no folder at all. To achieve
    // this behavior in Java, `**/` is replaced with `{**/,}`. The cases where `**` is directly next to normal characters
    // is still not handled correctly (matches more files than VSCode), but CommandCrafter doesn't use such patterns anywhere. Callers are expected to use `*` instead when `**` is not necessary.
    private fun findFiles(pattern: String, roots: List<Path>): CompletableFuture<Array<String>> {
        try {
            val (prefix, patternSuffix) = splitStaticPrefixFromGlob(fixDoubleStarGlobPattern(pattern))
            if(prefix.isAbsolute)
                return CompletableFuture.completedFuture(emptyArray()) // Can't search for absolute patterns, since patterns are matched against the relative path from the root
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$patternSuffix")

            val matched = roots.flatMap { root ->
                val resolved = root.resolve(prefix)
                if(!resolved.exists())
                    return@flatMap emptyList()
                Files.walk(resolved).use { stream ->
                    stream.filter { Files.isRegularFile(it) }.filter { path ->
                        matcher.matches(resolved.relativize(path))
                    }.map {
                        val prefixedPath = it.toString().ensurePrefixed('/')
                        val normalizedPath = if(EditorURI.IS_WINDOWS) prefixedPath.replace('\\', '/') else prefixedPath
                        "file://$normalizedPath"
                    }.toList()
                }
            }
            return CompletableFuture.completedFuture(matched.toTypedArray())
        } catch(e: IOException) {
            return CompletableFuture.failedFuture(e)
        } catch(e: InvalidPathException) {
            return CompletableFuture.failedFuture(e)
        }
    }

    /**
     * Checks if a glob pattern starts with a static path, which can be parsed separately.
     * Returns a pair of the static path and the remaining pattern.
     */
    private fun splitStaticPrefixFromGlob(pattern: String): Pair<Path, String> {
        val globChars = CharSet.of('*','?','[',']','{','}')
        val globIndex = pattern.indexOfFirst { it in globChars } // Doesn't handle escaped chars, should rarely matter
        if(globIndex == -1)
            return Path.of(pattern) to ""

        val staticPathEnd = pattern.lastIndexOf('/', globIndex)
        // +1 to include last separator in static path, which also makes sure the remaining pattern is relative
        // This also works if staticPathEnd == -1, since the path will just act as `.` and the pattern doesn't change
        return Path.of(pattern.substring(0, staticPathEnd + 1)) to pattern.substring(staticPathEnd + 1)
    }

    // Replaces **/ in paths with `{**/,}` (whilst taking into account escape characters)
    // to match VSCode's behavior
    private fun fixDoubleStarGlobPattern(pattern: String): String {
        val fixed = StringBuilder(pattern.length)
        var i = 0
        while(i < pattern.length) {
            if(pattern[i] == '\\') {
                fixed.append('\\')
                if(i + 1 >= pattern.length)
                    break
                fixed.append(pattern[i + 1])
                i += 2
                continue
            }
            if(pattern.startsWith("**/", i)) {
                fixed.append("{**/,}")
                i += 3
                continue
            }
            fixed.append(pattern[i])
            i += 1
        }
        return fixed.toString()
    }

    override fun fileExists(uri: String): CompletableFuture<Boolean> {
        val parsedUri = EditorURI.parseURI(uri)
        if(parsedUri.scheme != "file")
            return CompletableFuture.completedFuture(false)
        val path = parsedUri.parseLocalPath()
            ?: return CompletableFuture.completedFuture(false)
        val exists = absRoots.any { root ->
            if(path.isAbsolute && !path.startsWith(root))
                return@any false
            Files.exists(root.resolve(path))
        }
        return CompletableFuture.completedFuture(exists)
    }

    override fun getFileContent(uri: String): CompletableFuture<String> {
        val parsedUri = EditorURI.parseURI(uri)
        if(parsedUri.scheme != "file")
            return CompletableFuture.failedFuture(IOException("Unknown file schema: ${parsedUri.scheme}"))
        val path = parsedUri.parseLocalPath()
            ?: return CompletableFuture.failedFuture(IOException("Invalid local path in uri: $uri"))
        return try {
            CompletableFuture.completedFuture(Files.readString(path))
        } catch(e: IOException) {
            CompletableFuture.failedFuture(e)
        }
    }
}