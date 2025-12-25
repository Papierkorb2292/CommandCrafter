package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.commands.execution.ExecutionContext

class CommandExecutionContextContinueCallback(val context: ExecutionContext<*>) : () -> Unit {
    override fun invoke() {
        try {
            context.runCommandQueue()
        } catch(e: Throwable) {
            if(e !is ExecutionPausedThrowable) {
                throw e
            }
        }
    }
}