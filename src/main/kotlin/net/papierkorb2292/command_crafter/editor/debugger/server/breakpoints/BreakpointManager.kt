package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerNetworkDebugConnection
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.BreakpointEventArgumentsReason
import org.eclipse.lsp4j.debug.SourceBreakpoint
import java.util.*
import java.util.concurrent.CompletableFuture

class BreakpointManager<TBreakpointLocation>(
    val parser: (
        queue: Queue<ServerBreakpoint<TBreakpointLocation>>,
        fileId: Identifier,
        fileSourceReference: Int?,
    ) -> List<Breakpoint>
) {
    val breakpoints: MutableMap<ServerPlayNetworkHandler, MutableMap<Identifier, MutableMap<Int?, MutableList<ServerBreakpoint<TBreakpointLocation>>>>> = mutableMapOf()

    fun removePlayer(player: ServerPlayerEntity) {
        breakpoints.remove(player.networkHandler)
    }

    fun getOrCreateSourceFileBreakpoints(player: ServerPlayerEntity, sourceFile: Identifier, sourceReference: Int? = INITIAL_SOURCE_REFERENCE) = breakpoints.getOrPut(player.networkHandler, ::mutableMapOf).getOrPut(sourceFile, ::mutableMapOf).getOrPut(sourceReference, ::mutableListOf)

    private fun reserveBreakpointIds(debuggerConnection: ServerNetworkDebugConnection, count: Int) =
        if(count == 0) CompletableFuture.completedFuture(0)
        else debuggerConnection.reserveBreakpointIds(count)

    fun onBreakpointUpdate(
        breakpoints: Array<UnparsedServerBreakpoint>,
        debuggerConnection: ServerNetworkDebugConnection,
        player: ServerPlayerEntity,
        sourceFile: Identifier,
        sourceReference: Int?,
    ): List<Breakpoint> {
        if(breakpoints.isEmpty()) {
            removeBreakpoints(player, sourceFile)
            return emptyList()
        }
        val fileBreakpoints = getOrCreateSourceFileBreakpoints(player, sourceFile, sourceReference)
        val prevBreakpoints = fileBreakpoints.mapTo(HashSet()) { it.unparsed.id }
        fileBreakpoints.clear()
        val (sortedBreakpoints, unsortIndices) = getSorted(breakpoints, debuggerConnection)
        fileBreakpoints.addAll(sortedBreakpoints)

        if(sourceReference == INITIAL_SOURCE_REFERENCE) {
            val removedIds = prevBreakpoints - breakpoints.mapTo(HashSet()) { it.id }
            val added = breakpoints.filterNot { it.id in prevBreakpoints }
            if(removedIds.isNotEmpty() || added.isNotEmpty()) {
                for((subSourceReference, subSourceBreakpoints) in this.breakpoints[player.networkHandler]!![sourceFile]!!) {
                    if(subSourceReference == INITIAL_SOURCE_REFERENCE) continue
                    val modified = subSourceBreakpoints.removeAll { it.unparsed.id in removedIds } || added.isNotEmpty()
                    if(!modified) continue
                    reserveBreakpointIds(debuggerConnection, added.size).thenApply { addedIdStart ->
                        val addedCopied = added.mapIndexed { index, unparsed ->
                            UnparsedServerBreakpoint(addedIdStart + index, subSourceReference, unparsed.sourceBreakpoint)
                        }
                        val updatedBreakpoints = onBreakpointUpdate(
                            (subSourceBreakpoints.map { it.unparsed } + addedCopied).toTypedArray(),
                            debuggerConnection,
                            player,
                            sourceFile,
                            subSourceReference
                        )
                        sendNewSourceReferenceBreakpoints(
                            updatedBreakpoints.filter { it.id in addedIdStart until addedIdStart + added.size },
                            subSourceReference,
                            debuggerConnection,
                            sourceFile
                        )
                        removedIds.forEach {
                            debuggerConnection.updateReloadedBreakpoint(BreakpointEventArguments().apply {
                                breakpoint = Breakpoint().apply { id = it }
                                reason = BreakpointEventArgumentsReason.REMOVED
                            })
                        }
                    }
                }
            }
        }

        val parsed = parser(sortedBreakpoints, sourceFile, sourceReference)
        return List(parsed.size) {
            parsed[unsortIndices[it]]
        }
    }

    fun addNewSourceReferenceBreakpoints(
        breakpoints: List<SourceBreakpoint>,
        debuggerConnection: ServerNetworkDebugConnection,
        sourceFile: Identifier,
        sourceReference: Int?,
    ) {
        reserveBreakpointIds(debuggerConnection, breakpoints.size).thenApply { addedIdStart ->
            val unparsed = breakpoints.mapIndexed { index, sourceBreakpoint ->
                UnparsedServerBreakpoint(
                    addedIdStart + index,
                    sourceReference,
                    sourceBreakpoint
                )
            }
            sendNewSourceReferenceBreakpoints(onBreakpointUpdate(
                unparsed.toTypedArray(),
                debuggerConnection,
                debuggerConnection.player,
                sourceFile,
                sourceReference
            ), sourceReference, debuggerConnection, sourceFile)
        }
    }

    private fun sendNewSourceReferenceBreakpoints(
        breakpoints: List<Breakpoint>,
        breakpointSourceReference: Int?,
        debuggerConnection: ServerNetworkDebugConnection,
        sourceFile: Identifier,
    ) {
        breakpoints.forEach {
            debuggerConnection.updateReloadedBreakpoint(BreakpointEventArguments().apply {
                if(it.source == null) {
                    CommandCrafter.LOGGER.error(
                        """
                        'source' of new sourceReference breakpoint is null, but must not be null to add breakpoint!!
                        Error happened in file '$sourceFile' in line ${it.line}
                        with sourceReference '$breakpointSourceReference' from player '${debuggerConnection.player.name.string}'
                        """.trimIndent()
                    )
                }
                breakpoint = it
                reason = BreakpointEventArgumentsReason.NEW
            })
        }
    }

    fun getSorted(
        sourceBreakpoints: Array<UnparsedServerBreakpoint>,
        debuggerConnection: ServerNetworkDebugConnection,
    ): Pair<LinkedList<ServerBreakpoint<TBreakpointLocation>>, IntArray> {
        // In case the breakpoints aren't ordered according to
        // their position in the file, they are sorted for parsing
        // and this array is used to restore the initial ordering afterward
        val unsortIndices = IntArray(sourceBreakpoints.size)

        val serverBreakpoints = sourceBreakpoints.asSequence().withIndex().sortedWith { o1, o2 ->
            val sourceBreakpoint1 = o1.value.sourceBreakpoint
            val sourceBreakpoint2 = o2.value.sourceBreakpoint
            if(sourceBreakpoint1.line < sourceBreakpoint2.line) {
                -1
            } else if(sourceBreakpoint1.line > sourceBreakpoint2.line) {
                1
            } else {
                sourceBreakpoint1.column.compareTo(sourceBreakpoint2.column)
            }
        }.mapIndexedTo(LinkedList()) { sortedIndex, breakpoint ->
            val unparsedBreakpoint = breakpoint.value
            unsortIndices[breakpoint.index] = sortedIndex
            ServerBreakpoint<TBreakpointLocation>(unparsedBreakpoint, debuggerConnection)
        }

        return serverBreakpoints to unsortIndices
    }

    fun setBreakpointsAndSort(
        player: ServerPlayerEntity,
        sourceFile: Identifier,
        sourceBreakpoints: Array<UnparsedServerBreakpoint>,
        debuggerConnection: ServerNetworkDebugConnection,
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

    fun removeBreakpoints(player: ServerPlayerEntity, id: Identifier, sourceReference: Int? = INITIAL_SOURCE_REFERENCE) {
        val playerBreakpoints = breakpoints[player.networkHandler] ?: return
        val sources = playerBreakpoints[id] ?: return
        if(sourceReference != INITIAL_SOURCE_REFERENCE) {
            val prevBreakpoints = (sources[INITIAL_SOURCE_REFERENCE] ?: return).mapTo(HashSet()) { it.unparsed.id }
            val sourcesIt = sources.iterator()
            while(sourcesIt.hasNext()) {
                val source = sourcesIt.next()
                if(source.key == INITIAL_SOURCE_REFERENCE) {
                    sourcesIt.remove()
                    continue
                }
                source.value.removeAll { it.unparsed.id in prevBreakpoints }
                if(source.value.isEmpty()) sourcesIt.remove()
            }
        } else {
            sources.remove(INITIAL_SOURCE_REFERENCE)
        }
        if(sources.isNotEmpty()) return
        playerBreakpoints.remove(id)
        if(playerBreakpoints.isNotEmpty()) return
        breakpoints.remove(player.networkHandler)
    }
}