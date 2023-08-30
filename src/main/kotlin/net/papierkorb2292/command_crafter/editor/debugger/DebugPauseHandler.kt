package net.papierkorb2292.command_crafter.editor.debugger

import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import org.eclipse.lsp4j.debug.SteppingGranularity

/**
 * This interface is used to define the debugging behaviour
 * of a section of code.
 * To interact with Minecraft and the code, the handler gets
 * a context when being created in a [DebugPauseHandlerFactory]
 * (Extended by [DebugInformation]). Methods inherited from
 * [DebugPauseActions] are called corresponding to the debuggee's
 * requests.
 *
 * Implementations of "next", "stepIn" and "stepOut" should tell the context where to
 * pause next and prepare the stack trace according to the next pause location.
 */
interface DebugPauseHandler : DebugPauseActions {
    /**
     * This is called by the debugger once the desired pause location has been reached.
     * This location can have multiple consecutive invocations to pause on,
     * which this method lets the DebugPauseHandler choose from.
     * This method can also move the desired pause location.
     *
     * If the DebugPauseHandler doesn't stop at any of these invocations, the debugger
     * should call [findNextPauseLocation], as the location is considered to be skipped.
     */
    fun shouldStopOnCurrentContext() = true

    /**
     * This is called by the debugger when the previously specified pause location
     * is no longer valid.
     *
     * This can happen when for example the previously specified
     * location to pause on has been skipped or when the execution has returned from
     * a subroutine that the DebugPauseHandler decided to step into.
     */
    fun findNextPauseLocation()

    /**
     * This is called by the debugger when a breakpoint is hit. It can be used
     * to set up the stack trace.
     */
    fun onBreakpoint()

    fun getStackFrames(): List<MinecraftStackFrame>

    class SkipAllDummy(val setNextPauseCallback: () -> Unit) : DebugPauseHandler {
        override fun next(granularity: SteppingGranularity) {}
        override fun stepIn(granularity: SteppingGranularity) {}
        override fun stepOut(granularity: SteppingGranularity) {}
        override fun continue_() {}
        override fun findNextPauseLocation() {}
        override fun onBreakpoint() {}
        override fun getStackFrames() = emptyList<MinecraftStackFrame>()


        override fun shouldStopOnCurrentContext(): Boolean {
            setNextPauseCallback()
            return false
        }
    }
}