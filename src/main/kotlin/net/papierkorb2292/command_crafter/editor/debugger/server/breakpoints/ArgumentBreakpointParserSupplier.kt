package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.minecraft.server.MinecraftServer
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation

interface ArgumentBreakpointParserSupplier {
    fun `command_crafter$getBreakpointParser`(argument: Any?, server: MinecraftServer): BreakpointParser<FunctionBreakpointLocation>?
}