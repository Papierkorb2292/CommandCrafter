package net.papierkorb2292.command_crafter.editor

import java.io.InputStream
import java.io.OutputStream

interface EditorConnection {
    val inputStream: InputStream
    val outputStream: OutputStream
    fun close()
}