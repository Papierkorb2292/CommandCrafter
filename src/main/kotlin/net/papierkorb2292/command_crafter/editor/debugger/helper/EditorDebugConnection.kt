package net.papierkorb2292.command_crafter.editor.debugger.helper

import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.server.MinecraftServer
import net.minecraft.commands.functions.MacroFunction
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseActions
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

interface EditorDebugConnection {
    val lifecycle: Lifecycle
    val oneTimeDebugTarget: DebugTarget?
    val nextSourceReference: Int
    val suspendServer: Boolean
    fun pauseStarted(actions: DebugPauseActions, args: StoppedEventArguments, variables: VariablesReferencer)
    fun pauseEnded()
    fun isPaused(): Boolean
    fun updateReloadedBreakpoint(update: BreakpointEventArguments)
    fun reserveBreakpointIds(count: Int): CompletableFuture<ReservedBreakpointIdStart>
    fun popStackFrames(stackFrames: Int)
    fun pushStackFrames(stackFrames: List<MinecraftStackFrame>)
    fun output(args: OutputEventArguments)
    fun onSourceReferenceAdded()

    companion object {
        val DEBUG_TARGET_PACKET_CODEC: StreamCodec<ByteBuf, DebugTarget> = StreamCodec.composite(
            PackContentFileType.PACKET_CODEC,
            DebugTarget::targetFileType,
            Identifier.STREAM_CODEC,
            DebugTarget::targetId,
            ByteBufCodecs.BOOL,
            DebugTarget::stopOnEntry,
            ::DebugTarget
        )
    }

    class Lifecycle {
        val configurationDoneEvent: CompletableFuture<Void> = CompletableFuture()
        val shouldExitEvent: CompletableFuture<ExitedEventArguments> = CompletableFuture()
    }

    data class DebugTarget(val targetFileType: PackContentFileType, val targetId: Identifier, val stopOnEntry: Boolean)
}

fun EditorDebugConnection.onPauseLocationSkipped() {
    output(OutputEventArguments().apply {
        category = OutputEventArgumentsCategory.IMPORTANT
        output = "Skipped pause location"
    })
}

fun EditorDebugConnection.setupOneTimeDebugTarget(server: MinecraftServer) {
    val oneTimeDebugTarget = oneTimeDebugTarget ?: return
    val pauseContext = PauseContext(server, this, oneTimeDebugTarget.stopOnEntry)
    lifecycle.configurationDoneEvent.thenRunAsync({
        PauseContext.currentPauseContext.set(pauseContext)
        try {
            runOneTimeDebugTarget(server, oneTimeDebugTarget)
        } catch (e: Throwable) {
            if(e !is ExecutionPausedThrowable) throw e
            PauseContext.wrapExecution(e)
        } finally {
            PauseContext.resetPauseContext()
        }
    }, server)
}

private fun EditorDebugConnection.runOneTimeDebugTarget(server: MinecraftServer, oneTimeDebugTarget: EditorDebugConnection.DebugTarget) {
    when(oneTimeDebugTarget.targetFileType) {
        PackContentFileType.FUNCTIONS_FILE_TYPE -> {
            val function = server.functions.get(oneTimeDebugTarget.targetId)
            function.ifPresentOrElse({
                if(it is MacroFunction<*>) {
                    output(OutputEventArguments().apply {
                        category = OutputEventArgumentsCategory.IMPORTANT
                        output = "Functions with macros can't be run directly"
                    })
                    return@ifPresentOrElse
                }
                server.functions.execute(it, server.createCommandSourceStack())
            }, {
                output(OutputEventArguments().apply {
                    category = OutputEventArgumentsCategory.IMPORTANT
                    output = "Function '${oneTimeDebugTarget.targetId}' not found"
                })
            })
        }
        else -> output(OutputEventArguments().apply {
            category = OutputEventArgumentsCategory.IMPORTANT
            output = "Tried to run unsupported debug target type: ${oneTimeDebugTarget.targetFileType}"
        })
    }
}

typealias ReservedBreakpointIdStart = Int
