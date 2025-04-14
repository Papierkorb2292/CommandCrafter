package net.papierkorb2292.command_crafter.editor

import net.papierkorb2292.command_crafter.CommandCrafter
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.SocketException

class SocketEditorConnectionType(val port: Int) : EditorConnectionAcceptor {

    private var serverSocket: ServerSocket? = null

    override fun accept(): EditorConnection? {
        val socket = try {
            serverSocket?.accept() ?: throw IllegalStateException("Server socket is not running")
        } catch(e: SocketException) {
            if(e.message == "Socket closed")
                return null
            CommandCrafter.LOGGER.error("Error accepting editor connection", e)
            return null
        }
        return object : EditorConnection {
            override val inputStream: InputStream
                get() = socket.getInputStream()
            override val outputStream: OutputStream
                get() = socket.getOutputStream()

            override fun close() {
                socket.close()
            }
        }
    }

    override fun isRunning(): Boolean {
        return !(serverSocket ?: return false).isClosed
    }
    override fun start() {
        try {
            serverSocket = ServerSocket(port)
        } catch(e: Exception) {
            CommandCrafter.LOGGER.error("Failed to start server socket for editor connections on port $port", e)
        }
    }

    override fun stop() {
        serverSocket?.close()
        serverSocket = null
    }
}