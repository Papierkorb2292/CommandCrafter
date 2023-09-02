package net.papierkorb2292.command_crafter.editor.debugger

import net.minecraft.server.MinecraftServer
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.*

/**
 * This interface is used to attach information to a parsed section
 * of code, for how it can be debugged. It has to parse and validate
 * breakpoints placed in this section and provide [DebugPauseHandler]s
 * for it.
 */
interface DebugInformation<TBreakpointLocation, TPauseContext> :
    BreakpointParser<TBreakpointLocation>,
    DebugPauseHandlerFactory<TPauseContext> {

    class Concat<L, C>(private val delegateParsers: List<BreakpointParser<L>>, private val pauseHandlerSelector: (C) -> DebugPauseHandlerFactory<C>) :
        DebugInformation<L, C> {
        override fun parseBreakpoints(breakpoints: Queue<ServerBreakpoint<L>>, server: MinecraftServer) =
            delegateParsers.flatMap { it.parseBreakpoints(breakpoints, server) }

        override fun createDebugPauseHandler(pauseContext: C) = object : DebugPauseHandler {
            private var currentPauseHandlerCreator: DebugPauseHandlerFactory<C>? = null
            private var currentPauseHandler: DebugPauseHandler? = null

            fun updatePauseHandler(): DebugPauseHandler {
                val newPauseHandlerCreator = pauseHandlerSelector(pauseContext)
                if (newPauseHandlerCreator != currentPauseHandlerCreator) {
                    currentPauseHandlerCreator = newPauseHandlerCreator
                    currentPauseHandler = null
                } else {
                    currentPauseHandler?.run { return this }
                }

                val newPauseHandler = newPauseHandlerCreator.createDebugPauseHandler(pauseContext)
                this.currentPauseHandler = newPauseHandler
                return newPauseHandler
            }
            override fun next(granularity: SteppingGranularity) {
                updatePauseHandler().next(granularity)
            }
            override fun stepIn(granularity: SteppingGranularity) {
                updatePauseHandler().stepIn(granularity)
            }
            override fun stepOut(granularity: SteppingGranularity) {
                updatePauseHandler().stepOut(granularity)
            }
            override fun continue_() {
                updatePauseHandler().continue_()
            }

            override fun findNextPauseLocation() {
                updatePauseHandler().findNextPauseLocation()
            }

            override fun getStackFrames()
                = updatePauseHandler().getStackFrames()

            override fun shouldStopOnCurrentContext()
                = updatePauseHandler().shouldStopOnCurrentContext()
        }
    }
}