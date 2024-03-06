package net.papierkorb2292.command_crafter.editor.debugger

import net.minecraft.server.MinecraftServer
import net.papierkorb2292.command_crafter.editor.debugger.server.FileContentReplacer
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*

/**
 * This interface is used to attach information to a parsed section
 * of code, for how it can be debugged. It has to parse and validate
 * breakpoints placed in this section and provide [DebugPauseHandler]s
 * for it.
 */
interface DebugInformation<TBreakpointLocation, TDebugFrame : PauseContext.DebugFrame> :
    BreakpointParser<TBreakpointLocation>,
    DebugPauseHandlerFactory<TDebugFrame> {

    class Concat<L : Any, F : PauseContext.DebugFrame>(private val delegateDebugInformations: List<DebugInformation<L, F>>, private val pauseHandlerSelector: (F) -> Int) :
        DebugInformation<L, F> {
        override fun parseBreakpoints(breakpoints: Queue<ServerBreakpoint<L>>, server: MinecraftServer, sourceReference: Int?) =
            delegateDebugInformations.flatMap { it.parseBreakpoints(breakpoints, server, sourceReference) }

        override fun createDebugPauseHandler(debugFrame: F): DebugPauseHandler = object : DebugPauseHandler, FileContentReplacer {
            private var currentPauseHandler: DebugPauseHandler? = null

            private var delegatePauseHandlers = delegateDebugInformations.map { it.createDebugPauseHandler(debugFrame) }

            fun updatePauseHandler(): DebugPauseHandler {
                val newPauseHandler = delegatePauseHandlers[pauseHandlerSelector(debugFrame)]
                if(newPauseHandler != currentPauseHandler) {
                    currentPauseHandler = newPauseHandler
                }
                return newPauseHandler
            }
            override fun next(granularity: SteppingGranularity) {
                updatePauseHandler().next(granularity)
            }
            override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
                updatePauseHandler().stepIn(granularity, targetId)
            }
            override fun stepOut(granularity: SteppingGranularity) {
                updatePauseHandler().stepOut(granularity)
            }
            override fun stepInTargets(frameId: Int)
                = updatePauseHandler().stepInTargets(frameId)

            override fun continue_() {
                updatePauseHandler().continue_()
            }

            override fun findNextPauseLocation() {
                updatePauseHandler().findNextPauseLocation()
            }

            override fun getStackFrames(sourceReference: Int?) =
                updatePauseHandler().getStackFrames(sourceReference)

            override fun onExitFrame() =
                delegatePauseHandlers.forEach { it.onExitFrame() }

            override fun shouldStopOnCurrentContext() =
                updatePauseHandler().shouldStopOnCurrentContext()

            override fun getReplacementData(path: String) =
                FileContentReplacer.concatReplacementData(
                    delegatePauseHandlers.asSequence().mapNotNull {
                        (it as? FileContentReplacer)?.getReplacementData(path)
                    }.toList()
                )
        }
    }
}