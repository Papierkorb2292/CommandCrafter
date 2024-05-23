package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.command.CommandExecutionContext

class CommandExecutionContextContinueCallback(val context: CommandExecutionContext<*>) : () -> Unit {
    override fun invoke() {
        try {
            context.run()
        } catch(e: Throwable) {
            if(e !is ExecutionPausedThrowable) {
                throw e
            }
        }
    }
}