package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.minecraft.server.MinecraftServer

interface DebugHandlerFactory {
    fun createDebugHandler(server: MinecraftServer): DebugHandler
}