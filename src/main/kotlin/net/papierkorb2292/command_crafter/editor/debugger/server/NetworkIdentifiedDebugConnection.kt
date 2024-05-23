package net.papierkorb2292.command_crafter.editor.debugger.server

import net.minecraft.server.network.ServerPlayNetworkHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection

interface NetworkIdentifiedDebugConnection : EditorDebugConnection {
    val networkHandler: ServerPlayNetworkHandler
}