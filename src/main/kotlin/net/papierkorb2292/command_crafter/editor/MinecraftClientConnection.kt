package net.papierkorb2292.command_crafter.editor

interface MinecraftClientConnection {
    val isConnectedToServer: Boolean
    fun reloadResources(params: ReloadResourcesParams)
}