package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import net.minecraft.commands.execution.EntryAction
import net.minecraft.commands.execution.ExecutionContext
import net.minecraft.commands.execution.Frame
import net.minecraft.commands.CommandSourceStack
import net.papierkorb2292.command_crafter.editor.debugger.server.CommandResultContainer
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext

/**
 * Pops debug frames until the specified depth is reached.
 * This command action is not discarded by [CommandExecutionContext.escape]
 */
class ExitDebugFrameCommandAction(
    private val targetDepth: Int,
    private val frameResultContainer: CommandResultContainer? = null,
    private val clearFrameResult: Boolean = true,
    private val afterExitCallback: (() -> Unit)? = null,
) : EntryAction<CommandSourceStack> {

    override fun execute(context: ExecutionContext<CommandSourceStack>, frame: Frame) {
        val pauseContext = PauseContext.currentPauseContext.get() ?: return
        frameResultContainer?.let {
            // A value of null means to the debug pause handler that no call
            // happened, thus, if the call didn't produce an output (meaning
            // the value is still null) it must be set to CommandResult(null),
            // so the debug pause handler knows a call without a result happened
            if(it.commandResult == null) {
                it.commandResult = CommandResult(null)
            }
        }
        while(pauseContext.debugFrameDepth > targetDepth) {
            pauseContext.popDebugFrame()
        }
        afterExitCallback?.invoke()
        if(clearFrameResult) {
            frameResultContainer?.commandResult = null
        }
    }
}