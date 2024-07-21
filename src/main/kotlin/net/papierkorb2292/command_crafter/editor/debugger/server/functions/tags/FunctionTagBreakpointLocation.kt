package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import net.minecraft.util.Identifier

/**
 * The location of a function tag breakpoint.
 *
 * It can be related to multiple entry indices in case other tags also include the source tag file,
 * in which case each of them have their own entry index.
 */
class FunctionTagBreakpointLocation(
    val entryIndexPerTag: Map<Identifier, Int>
)