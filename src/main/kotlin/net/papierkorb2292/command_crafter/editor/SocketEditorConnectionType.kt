package net.papierkorb2292.command_crafter.editor

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket

class SocketEditorConnectionType(val port: Int) : EditorConnectionAcceptor {

    private var serverSocket: ServerSocket = ServerSocket(port)

    override fun accept(): EditorConnection {
        val socket = serverSocket.accept()
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

    override fun isRunning() = !serverSocket.isClosed
    override fun start() {
        serverSocket = ServerSocket(port)
    }

    override fun stop() {
        serverSocket.close()
    }
}