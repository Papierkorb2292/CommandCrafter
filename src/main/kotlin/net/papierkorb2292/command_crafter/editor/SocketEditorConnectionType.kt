package net.papierkorb2292.command_crafter.editor

import java.net.ServerSocket

class SocketEditorConnectionType(val port: Int) : EditorConnectionAcceptor {

    private var serverSocket: ServerSocket = ServerSocket(port)

    override fun accept(): EditorConnection {
        val socket = serverSocket.accept()
        return EditorConnection(socket.getInputStream(), socket.getOutputStream())
    }

    override fun isRunning() = !serverSocket.isClosed
    override fun start() {
        serverSocket = ServerSocket(port)
    }

    override fun stop() {
        serverSocket.close()
    }
}