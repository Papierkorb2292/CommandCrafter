package net.papierkorb2292.command_crafter.editor.processing.codecmod

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

class MixinGenStreamHandler(val lookup: Map<String, ByteArray>) : URLStreamHandler() {
    fun toURL() = URL.of(URI.create("magic-cc:///"), this)

    override fun openConnection(u: URL): URLConnection? {
        val mixin = lookup[u.path] ?: return null
        return object : URLConnection(u) {
            override fun connect() {
                throw UnsupportedOperationException()
            }

            override fun getInputStream(): InputStream = ByteArrayInputStream(mixin)
        }
    }
}