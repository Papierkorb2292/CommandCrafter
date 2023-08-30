package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import java.util.*

class BreakpointMap<TBreakpointLocation> {
    val breakpoints: MutableMap<ServerPlayerEntity, MutableMap<Identifier, MutableList<ServerBreakpoint<TBreakpointLocation>>>> = mutableMapOf()

    fun removePlayer(player: ServerPlayerEntity) {
        breakpoints.remove(player)
    }

    fun getFilteredBreakpoints(filter: (TBreakpointLocation) -> Boolean) = breakpoints.values.asSequence().flatMap { it.values }.flatten().filter {
        filter((it.action ?: return@filter false).location)
    }
    fun getOrCreateSourceFileBreakpoints(player: ServerPlayerEntity, sourceFile: Identifier) = breakpoints.getOrPut(player, ::mutableMapOf).getOrPut(sourceFile, ::mutableListOf)

    fun setBreakpointsAndSort(
        player: ServerPlayerEntity,
        sourceFile: Identifier,
        sourceBreakpoints: Array<UnparsedServerBreakpoint>,
        debuggerConnection: EditorDebugConnection,
    ): Pair<Queue<ServerBreakpoint<TBreakpointLocation>>, IntArray> {
        val serverBreakpointsQueue: Queue<ServerBreakpoint<TBreakpointLocation>> = ArrayDeque(sourceBreakpoints.size)

        // In case the breakpoints aren't ordered according to
        // their position in the file, they are sorted for parsing
        // and this array is used to restore the initial ordering afterwards
        val unsortIndices = IntArray(sourceBreakpoints.size)

        val parsedBreakpoints = getOrCreateSourceFileBreakpoints(player, sourceFile)
        parsedBreakpoints.clear()

        sourceBreakpoints.asSequence().withIndex().sortedWith { o1, o2 ->
            val sourceBreakpoint1 = o1.value.sourceBreakpoint
            val sourceBreakpoint2 = o2.value.sourceBreakpoint
            if(sourceBreakpoint1.line < sourceBreakpoint2.line) {
                -1
            } else if(sourceBreakpoint1.line > sourceBreakpoint2.line) {
                1
            } else {
                sourceBreakpoint1.column.compareTo(sourceBreakpoint2.column)
            }
        }.forEachIndexed { sortedIndex, breakpoint ->
            val unparsedBreakpoint = breakpoint.value
            val functionBreakpoint = ServerBreakpoint<TBreakpointLocation>(unparsedBreakpoint, debuggerConnection)
            serverBreakpointsQueue.add(functionBreakpoint)
            parsedBreakpoints.add(functionBreakpoint)
            unsortIndices[breakpoint.index] = sortedIndex
        }

        return serverBreakpointsQueue to unsortIndices
    }

    fun removeSourceFile(player: ServerPlayerEntity, id: Identifier) {
        breakpoints[player]?.remove(id)
    }
}