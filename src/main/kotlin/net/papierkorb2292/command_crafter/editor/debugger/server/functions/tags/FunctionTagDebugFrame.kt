package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.datafixers.util.Either
import net.minecraft.command.CommandAction
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionPausedThrowable
import net.papierkorb2292.command_crafter.editor.debugger.helper.IdentifiedDebugInformationProvider
import net.papierkorb2292.command_crafter.editor.debugger.helper.getDebugManager
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResult
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.mixin.MinecraftServerAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.MacroAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ReturnValueAdderAccessor

class FunctionTagDebugFrame(
    val pauseContext: PauseContext,
    val tagId: Identifier,
    val macroNames: List<String>,
    val macroArguments: List<String>,
    val commandSource: ServerCommandSource,
    private val unpauseCallback: () -> Unit,
    var returnValueAdder: ReturnValueAdderAccessor? = null
) : PauseContext.DebugFrame {

    companion object {
        fun pushFrameForCommandArgumentIfIsTag(
            context: CommandContext<ServerCommandSource>,
            functionArgumentName: String,
            pauseContext: PauseContext,
            macros: NbtCompound?,
            unpauseCallback: () -> Unit
        ): Boolean {
            val functionArgument = try {
                CommandFunctionArgumentType.getFunctionOrTag(context, functionArgumentName)
            } catch(e: CommandSyntaxException) {
                return false
            }
            if(functionArgument.second.right().isPresent) {
                pauseContext.pushDebugFrame(FunctionTagDebugFrame(
                    pauseContext,
                    functionArgument.first,
                    macros?.keys?.toList() ?: emptyList(),
                    macros?.keys?.map {
                        MacroAccessor.callToString(macros.get(it))
                    } ?: emptyList(),
                    context.source,
                    unpauseCallback
                ))
                return true
            }
            return false
        }

        fun <TSource> wrapCommandActionWithTagPauseCheck(action: CommandAction<TSource>, entryIndex: Int): CommandAction<TSource> {
            val accumulatedResultUpdater = getAccumulatedResultSingleTimeUpdater()
            return CommandAction { context, frame ->
                val pauseContext = PauseContext.currentPauseContext.getOrNull()
                val tagDebugFrame = (pauseContext?.peekDebugFrame() as? FunctionTagDebugFrame)
                if(tagDebugFrame != null) {
                    accumulatedResultUpdater(tagDebugFrame)
                    tagDebugFrame.checkPause(entryIndex)
                    pauseContext.commandResult = null
                }
                action.execute(context, frame)
            }
        }

        fun getLastTagPauseCommandAction(): CommandAction<ServerCommandSource> {
            val accumulatedResultUpdater = getAccumulatedResultSingleTimeUpdater()
            return CommandAction<ServerCommandSource> { _, _ ->
                val pauseContext = PauseContext.currentPauseContext.getOrNull() ?: return@CommandAction
                val tagDebugFrame = pauseContext.peekDebugFrame() as? FunctionTagDebugFrame ?: return@CommandAction
                accumulatedResultUpdater(tagDebugFrame)
                val pauseIndex = pauseContext.server.commandFunctionManager.getTag(tagDebugFrame.tagId).size
                tagDebugFrame.checkPause(pauseIndex)
            }
        }

        val COPY_TAG_RESULT_TO_COMMAND_RESULT_COMMAND_ACTION = CommandAction<ServerCommandSource> { _, _ ->
            val pauseContext = PauseContext.currentPauseContext.getOrNull() ?: return@CommandAction
            val tagDebugFrame = (pauseContext.peekDebugFrame() as? FunctionTagDebugFrame) ?: return@CommandAction
            pauseContext.commandResult = CommandResult(tagDebugFrame.accumulatedResult)
        }

        fun getAccumulatedResultSingleTimeUpdater(): (FunctionTagDebugFrame) -> Unit {
            var hasUpdatedAccumulatedResult = false
            return { frame ->
                if(!hasUpdatedAccumulatedResult) {
                    frame.pauseContext.commandResult?.let {
                        frame.addFunctionResult(it)
                    }
                    hasUpdatedAccumulatedResult = true
                }
            }
        }
    }

    private var nextPauseIndex = -1
    private var lastPauseIndex = -1

    private var debugPauseHandler: DebugPauseHandler? = null

    var breakpoints: List<ServerBreakpoint<FunctionTagBreakpointLocation>>

    var currentEntryIndex = -1

    var accumulatedResult = false to 0
        private set

    fun addFunctionResult(result: CommandResult) {
        if(result.returnValue != null)
            // ReturnValueAdder in FunctionCommand always goes to successful=true as well
            accumulatedResult = true to (accumulatedResult.second + result.returnValue.second)
    }

    fun setAccumulatedResult(successful: Boolean, returnValue: Int) {
        accumulatedResult = successful to returnValue
        returnValueAdder?.apply {
            setSuccessful(successful)
            setReturnValue(returnValue)
        }
    }

    override fun onContinue(stackEntry: PauseContext.DebugFrameStack.Entry) {
        breakpoints = pauseContext.server.getDebugManager().functionTagDebugHandler.getTagBreakpoints(tagId)
    }

    init {
        breakpoints = pauseContext.server.getDebugManager().functionTagDebugHandler.getTagBreakpoints(tagId)
    }

    fun pauseOnEntryIndex(index: Int) {
        nextPauseIndex = index
        pauseContext.unpause()
    }

    fun checkPause(newEntryIndex: Int) {
        currentEntryIndex = newEntryIndex
        if(newEntryIndex == lastPauseIndex)
            return
        if(nextPauseIndex == newEntryIndex) {
            onPauseLocationReached()
            return
        }
        for(breakpoint in breakpoints) {
            val action = breakpoint.action ?: continue
            if(action.location.entryIndexPerTag[tagId] == newEntryIndex) {
                onBreakpointHit(breakpoint)
                return
            }
        }
    }

    fun onPauseLocationReached() {
        if(pauseContext.initPauseLocationReached())
            startPause()
    }

    fun onBreakpointHit(breakpoint: ServerBreakpoint<FunctionTagBreakpointLocation>) {
        if(pauseContext.initBreakpointPause(breakpoint))
            startPause()
    }

    private fun startPause() {
        nextPauseIndex = -1
        lastPauseIndex = currentEntryIndex
        pauseContext.suspend() { CommandExecutionPausedThrowable(pauseContext.executionWrapper) }
    }

    override fun getDebugPauseHandler(): DebugPauseHandler {
        debugPauseHandler?.run { return this }
        @Suppress("UNCHECKED_CAST")
        val handler = ((pauseContext.server as MinecraftServerAccessor)
            .resourceManagerHolder
            .dataPackContents
            .functionLoader as IdentifiedDebugInformationProvider<*, FunctionTagDebugFrame>)
            .`command_crafter$getDebugInformation`(tagId)!!
            .createDebugPauseHandler(this)
        debugPauseHandler = handler
        return handler
    }

    override fun unpause() {
        pauseContext.server.execute {
            pauseContext.executionWrapper.runCallback(unpauseCallback)
        }
    }

    override fun shouldWrapInSourceReference(path: String): Either<PauseContext.NewSourceReferenceWrapper, PauseContext.ExistingSourceReferenceWrapper>? {
        return null
    }

    override fun onExitFrame() {
        debugPauseHandler?.onExitFrame()
    }
}