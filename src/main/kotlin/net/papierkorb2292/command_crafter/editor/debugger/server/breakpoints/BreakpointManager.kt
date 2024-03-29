package net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints

import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.debugger.BreakpointParser
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
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
        debugConnection: EditorDebugConnection
    ) -> List<Breakpoint>
) {
    val fileBreakpoints: MutableMap<EditorDebugConnection, MutableMap<FileBreakpointSource, MutableList<ServerBreakpoint<TBreakpointLocation>>>> = mutableMapOf()
    val breakpoints: MutableMap<EditorDebugConnection, MutableMap<Identifier, MutableMap<Int?, MutableMap<BreakpointParser<TBreakpointLocation>, AddedBreakpointList<TBreakpointLocation>>>>> = mutableMapOf()

    fun removeDebugConnection(debugConnection: EditorDebugConnection) {
        breakpoints.remove(debugConnection)
        fileBreakpoints.remove(debugConnection)
    }

    fun getOrCreateResourceBreakpoints(debugConnection: EditorDebugConnection, sourceFile: Identifier, sourceReference: Int? = INITIAL_SOURCE_REFERENCE) = breakpoints.getOrPut(debugConnection, ::mutableMapOf).getOrPut(sourceFile, ::mutableMapOf).getOrPut(sourceReference, ::mutableMapOf)

    fun setParserBreakpoints(
        resourceId: Identifier,
        sourceReference: Int? = INITIAL_SOURCE_REFERENCE,
        breakpointParser: BreakpointParser<TBreakpointLocation>,
        breakpoints: AddedBreakpointList<TBreakpointLocation>,
        debugConnection: EditorDebugConnection
    ) {
        if(breakpoints.list.isEmpty()) {
            removeBreakpoints(debugConnection, resourceId, sourceReference, breakpointParser)
            return
        }
        val resourceBreakpoints = getOrCreateResourceBreakpoints(debugConnection, resourceId, sourceReference)
        val prevBreakpoints = resourceBreakpoints.put(breakpointParser, breakpoints) ?: return
        if(sourceReference != INITIAL_SOURCE_REFERENCE) return

        val prevBreakpointIds = prevBreakpoints.list.mapTo(HashSet()) { it.unparsed.id }
        val removedIds = prevBreakpointIds - breakpoints.list.mapTo(HashSet()) { it.unparsed.id }
        val added = breakpoints.list.filterNot { it.unparsed.id in prevBreakpointIds }
        if(removedIds.isNotEmpty() || added.isNotEmpty()) {
            for((subSourceReference, subSourceBreakpoints) in this.breakpoints[debugConnection]!![resourceId]!!) {
                if(subSourceReference == INITIAL_SOURCE_REFERENCE) continue
                val breakpointParserBreakpoints =
                    if(added.isEmpty()) subSourceBreakpoints[breakpointParser] ?: continue
                    else subSourceBreakpoints.getOrPut(breakpointParser) { AddedBreakpointList(mutableListOf()) }
                val modified = breakpointParserBreakpoints.list.removeAll { it.unparsed.id in removedIds } || added.isNotEmpty()
                if(!modified) continue
                reserveBreakpointIds(debugConnection, added.size).thenApply { addedIdStart ->
                    val addedCopied = added.mapIndexed { index, original ->
                        UnparsedServerBreakpoint(addedIdStart + index, subSourceReference, original.unparsed.sourceBreakpoint)
                    }
                    val updatedBreakpoints = onBreakpointUpdate(
                        (subSourceBreakpoints.flatMap { breakpointParserBreakpointsEntry ->
                            breakpointParserBreakpointsEntry.value.list.map { serverBreakpoint ->
                                serverBreakpoint.unparsed
                            }
                        } + addedCopied).toTypedArray(),
                        debugConnection,
                        resourceId,
                        subSourceReference
                    )
                    sendNewSourceReferenceBreakpoints(
                        updatedBreakpoints.filter { it.id in addedIdStart until addedIdStart + added.size },
                        subSourceReference,
                        debugConnection,
                        resourceId
                    )
                    removedIds.forEach {
                        debugConnection.updateReloadedBreakpoint(BreakpointEventArguments().apply {
                            breakpoint = Breakpoint().apply { id = it }
                            reason = BreakpointEventArgumentsReason.REMOVED
                        })
                    }
                }
            }
        }
    }

    private fun reserveBreakpointIds(debuggerConnection: EditorDebugConnection, count: Int) =
        if(count == 0) CompletableFuture.completedFuture(0)
        else debuggerConnection.reserveBreakpointIds(count)

    private fun setFileBreakpoints(
        resourceId: Identifier,
        sourceReference: Int?,
        sortedBreakpoints: List<ServerBreakpoint<TBreakpointLocation>>,
        debugConnection: EditorDebugConnection
    ) {
        if(sortedBreakpoints.isEmpty()) {
            val editorFileBreakpoints = fileBreakpoints[debugConnection] ?: return
            editorFileBreakpoints.remove(FileBreakpointSource(resourceId, sourceReference))
            if(editorFileBreakpoints.isEmpty()) fileBreakpoints.remove(debugConnection)
            return
        }
        fileBreakpoints.getOrPut(debugConnection, ::mutableMapOf)[FileBreakpointSource(resourceId, sourceReference)] = sortedBreakpoints.toMutableList()
    }

    fun onBreakpointUpdate(
        breakpoints: Array<UnparsedServerBreakpoint>,
        debugConnection: EditorDebugConnection,
        resourceId: Identifier,
        sourceReference: Int?,
    ): List<Breakpoint> {
        val (sortedBreakpoints, unsortIndices) = getSorted(breakpoints, debugConnection)
        setFileBreakpoints(resourceId, sourceReference, sortedBreakpoints, debugConnection)
        val parsed = parser(sortedBreakpoints, resourceId, sourceReference, debugConnection)
        return List(parsed.size) {
            parsed[unsortIndices[it]]
        }
    }

    fun addNewSourceReferenceBreakpoints(
        breakpoints: List<SourceBreakpoint>,
        debuggerConnection: EditorDebugConnection,
        resourceId: Identifier,
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
                resourceId,
                sourceReference
            ), sourceReference, debuggerConnection, resourceId)
        }
    }

    private fun sendNewSourceReferenceBreakpoints(
        breakpoints: List<Breakpoint>,
        breakpointSourceReference: Int?,
        debugConnection: EditorDebugConnection,
        resourceId: Identifier,
    ) {
        breakpoints.forEach {
            debugConnection.updateReloadedBreakpoint(BreakpointEventArguments().apply {
                if(it.source == null) {
                    CommandCrafter.LOGGER.error(
                        """
                        'source' of new sourceReference breakpoint is null, but must not be null to add breakpoint!!
                        Error happened in resource '$resourceId' in line ${it.line}
                        with sourceReference '$breakpointSourceReference' and debugConnection '$debugConnection'
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
        debugConnection: EditorDebugConnection
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
            ServerBreakpoint<TBreakpointLocation>(unparsedBreakpoint, debugConnection)
        }

        return serverBreakpoints to unsortIndices
    }

    fun removeBreakpoints(debugConnection: EditorDebugConnection, id: Identifier, sourceReference: Int? = INITIAL_SOURCE_REFERENCE) {
        val playerBreakpoints = breakpoints[debugConnection] ?: return
        val sources = playerBreakpoints[id] ?: return
        val sourceReferenceBreakpoints = sources[sourceReference] ?: return
        if(sourceReference == INITIAL_SOURCE_REFERENCE) {
            val prevBreakpoints = (sourceReferenceBreakpoints.values.flatMap { it.list }).mapTo(HashSet()) { it.unparsed.id }
            val sourcesIt = sources.iterator()
            while(sourcesIt.hasNext()) {
                val source = sourcesIt.next()
                if(source.key == INITIAL_SOURCE_REFERENCE) {
                    sourcesIt.remove()
                    continue
                }
                val sourceEntriesIt = source.value.iterator()
                while(sourceEntriesIt.hasNext()) {
                    val sourceEntry = sourceEntriesIt.next()
                    sourceEntry.value.list.removeAll { it.unparsed.id in prevBreakpoints }
                    if(sourceEntry.value.list.isEmpty()) sourceEntriesIt.remove()
                }
                if(source.value.isEmpty()) sourcesIt.remove()
            }
        } else {
            sources.remove(sourceReference)
        }
        if(sources.isNotEmpty()) return
        playerBreakpoints.remove(id)
        if(playerBreakpoints.isNotEmpty()) return
        breakpoints.remove(debugConnection)
    }

    fun removeBreakpoints(debugConnection: EditorDebugConnection, id: Identifier, sourceReference: Int? = INITIAL_SOURCE_REFERENCE, breakpointParser: BreakpointParser<TBreakpointLocation>) {
        val playerBreakpoints = breakpoints[debugConnection] ?: return
        val sources = playerBreakpoints[id] ?: return
        val sourceReferenceBreakpoints = sources[sourceReference] ?: return
        if(sourceReference == INITIAL_SOURCE_REFERENCE) {
            val prevBreakpoints = (sourceReferenceBreakpoints[breakpointParser] ?: return).list.mapTo(HashSet()) { it.unparsed.id }
            val sourcesIt = sources.iterator()
            while(sourcesIt.hasNext()) {
                val source = sourcesIt.next()
                if(source.key == INITIAL_SOURCE_REFERENCE) {
                    source.value.remove(breakpointParser)
                } else {
                    source.value[breakpointParser]?.list?.removeAll { it.unparsed.id in prevBreakpoints }
                }
                if(source.value.isEmpty()) sourcesIt.remove()
            }
        } else {
            sourceReferenceBreakpoints.remove(breakpointParser)
            if(sourceReferenceBreakpoints.isEmpty()) sources.remove(sourceReference)
        }
        if(sources.isNotEmpty()) return
        playerBreakpoints.remove(id)
        if(playerBreakpoints.isNotEmpty()) return
        breakpoints.remove(debugConnection)
    }

    fun reloadBreakpoints() {
        for((debugConnection, editorFileBreakpoints) in fileBreakpoints) {
            for((fileBreakpointSource, breakpoints) in editorFileBreakpoints) {
                parser(
                    LinkedList(breakpoints),
                    fileBreakpointSource.fileId,
                    fileBreakpointSource.sourceReference,
                    debugConnection
                ).forEach { breakpoint ->
                    debugConnection.updateReloadedBreakpoint(BreakpointEventArguments().apply {
                        this.breakpoint = breakpoint
                        this.reason = BreakpointEventArgumentsReason.CHANGED
                    })
                }
            }
        }
    }

    data class FileBreakpointSource(val fileId: Identifier, val sourceReference: Int?)

    data class AddedBreakpointList<TBreakpointLocation>(val list: MutableList<ServerBreakpoint<TBreakpointLocation>>) {
        constructor() : this(mutableListOf())
    }
}