package net.papierkorb2292.command_crafter.editor

interface EditorService {
    fun setMinecraftServerConnection(connection: MinecraftServerConnection)
    fun onClosed() { }
    fun leave() {}
}