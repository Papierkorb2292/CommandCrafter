package net.papierkorb2292.command_crafter.editor

import net.minecraft.util.Util
import org.eclipse.lsp4j.jsonrpc.util.ToStringBuilder
import java.net.URLDecoder
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * This class represents URIs send by the editor.
 *
 * An implementation similar to [VSCode Uri](https://github.com/microsoft/vscode-uri)
 * is used.
 *
 * Note that the path separator is always '/' and absolute paths always start with '/' (also on Windows, the drive letter will come after a '/')
 */
class EditorURI private constructor(
    scheme: String,
    /**
     * authority is the 'www.example.com' part of 'http://www.example.com/some/path?query#fragment'.
     * The part between the first double slashes and the next slash.
     */
    val authority: String,

    path: String,
    /**
     * query is the 'query' part of 'http://www.example.com/some/path?query#fragment'.
     */
    val query: String,
    /**
     * fragment is the 'fragment' part of 'http://www.example.com/some/path?query#fragment'.
     */
    val fragment: String,

    strict: Boolean = false
) {
    /**
     * scheme is the 'http' part of 'http://www.example.com/some/path?query#fragment'.
     * The part before the first colon.
     */
    val scheme = schemeFix(scheme, strict)
    /**
     * path is the '/some/path' part of 'http://www.example.com/some/path?query#fragment'.
     */
    val path = referenceResolution(scheme, path)

    companion object {
        private val uriRegex = Regex("^(([^:/?#]+?):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
        private val encodedAsHex = Regex("(%[0-9A-Za-z][0-9A-Za-z])+")
        private val IS_WINDOWS = Util.getPlatform() == Util.OS.WINDOWS

        fun parseURI(uri: String, strict: Boolean = false): EditorURI {
            val match = uriRegex.matchEntire(uri)
                ?: return EditorURI("", "", "", "", "")
            val scheme = match.groupValues[2]
            val authority = percentDecode(match.groupValues[4])
            val path = percentDecode(match.groupValues[5])
            val query = percentDecode(match.groupValues[7])
            val fragment = percentDecode(match.groupValues[9])
            return EditorURI(scheme, authority, path, query, fragment, strict)
        }

        fun percentDecode(str: String) =
            if(!str.contains(encodedAsHex)) str
            else str.replace(encodedAsHex) { decodeURIComponentGraceful(it.value) }

        fun decodeURIComponentGraceful(str: String): String {
            return try {
                URLDecoder.decode(str, "UTF-8")
            } catch(e: Exception) {
                if(str.length > 3)
                    str.substring(0, 3) + decodeURIComponentGraceful(str.substring(3))
                else
                    str
            }
        }

        fun schemeFix(scheme: String, strict: Boolean) =
            if(scheme.isEmpty() && !strict) "file"
            else scheme

        fun referenceResolution(scheme: String, path: String) =
            when(scheme) {
                "https", "http", "file" ->
                    if(path.isEmpty()) "/"
                    else if(path[0] != '/') "/$path"
                    else path
                else -> path
            }

        // VSCode paths always start with '/'. So on Windows this has to be removed to get the drive letter
        fun parseLocalPath(path: String): Path? =
            try {
                if(IS_WINDOWS) Path.of(path.trimStart('/')) else Path.of(path)
            } catch(e: InvalidPathException) {
                // This could happen if URI is invalid or if the editor actually runs on a different OS
                null
            }
    }

    fun parseLocalPath(): Path? = parseLocalPath(path)

    fun getParent(): EditorURI {
        // Trim everything after the last non-trailing slash, but keep the slash if it's the root path
        val lastSlashIndex = path.lastIndexOf('/', path.lastIndex - 1) // Trailing slashes should not be kept, unless it's the root path
        if(lastSlashIndex == -1) {
            return copyWithPath("/")
        }
        return copyWithPath(path.substring(0, lastSlashIndex + 1))
    }

    fun resolve(relativePath: String): EditorURI =
        if(relativePath == "") this
        else if(relativePath.startsWith("/")) copyWithPath(relativePath)
        else copyWithPath(pathWithTrailingSlash() + relativePath)

    fun startsWith(other: EditorURI): Boolean {
        if(scheme != other.scheme || authority != other.authority)
            return false
        return pathWithTrailingSlash().startsWith(other.pathWithTrailingSlash())
    }

    fun copyWithPath(path: String) = EditorURI(scheme, authority, path, query, fragment)

    private fun pathWithTrailingSlash() = if(path.endsWith("/")) path else "$path/"

    fun toPatternMatch(): String {
        val segments = path.split("/")
        val pathRegex = segments.joinToString("/") { segment ->
            if(segment == "**")
                return@joinToString ".+"
            val literalParts = segment.split("*")
            literalParts.joinToString("[^/]+") { Pattern.quote(it) }
        }
        val scheme = Pattern.quote(scheme)
        val authority = Pattern.quote(authority)
        return "$scheme://$authority$pathRegex"
    }

    override fun toString(): String {
        val query = if(query.isEmpty()) "" else "?$query"
        val fragment = if(fragment.isEmpty()) "" else "#$fragment"
        // Encode '#' and '?' in the path, since they have a special meaning. Note that URIs technically only allow very few characters in the path,
        // but this should be enough to communicate with the editor. It corresponds to VSCode's `encodeURIComponentMinimal`
        val encodedPath = path.replace("#", "%23").replace("?", "%3F")
        return "$scheme://$authority$encodedPath$query$fragment"
    }

    fun toDetailString(): String {
        val builder = ToStringBuilder(this)
        builder.add("scheme", scheme)
        builder.add("authority", authority)
        builder.add("path", path)
        builder.add("query", query)
        builder.add("fragment", fragment)
        return builder.toString()
    }
}