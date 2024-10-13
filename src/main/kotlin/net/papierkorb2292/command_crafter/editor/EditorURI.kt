package net.papierkorb2292.command_crafter.editor

import java.net.URLDecoder
import java.util.regex.Pattern

/**
 * This class represents URIs send by the editor.
 *
 * An implementation similar to [VSCode Uri](https://github.com/microsoft/vscode-uri)
 * is used.
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
    }

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
}