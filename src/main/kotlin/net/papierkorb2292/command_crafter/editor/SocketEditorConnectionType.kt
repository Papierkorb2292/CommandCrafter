package net.papierkorb2292.command_crafter.editor

import java.net.ServerSocket

class SocketEditorConnectionType(port: Int) : EditorConnectionAcceptor {

    private val serverSocket: ServerSocket

    init {
        serverSocket = ServerSocket(port)
    }

    override fun accept(): EditorConnection {
        val socket = serverSocket.accept()
        return EditorConnection(socket.getInputStream(), socket.getOutputStream())
    }

    override fun isRunning() = !serverSocket.isClosed

    fun close() {
        serverSocket.close()
    }
}