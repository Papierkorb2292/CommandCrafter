package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import com.mojang.brigadier.context.CommandContext
import com.mojang.datafixers.util.Either
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.command.CommandAction
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionPausedThrowable
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.getDebugManager
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResult
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.mixin.editor.debugger.MacroAccessor
import org.eclipse.lsp4j.Range

class FunctionTagDebugFrame(
    val pauseContext: PauseContext,
    val tagId: Identifier,
    val macroNames: List<String>,
    val macroArguments: List<String>,
    val commandSource: ServerCommandSource,
    private val unpauseCallback: () -> Unit
) : PauseContext.DebugFrame {

    companion object {
        fun pushFrameForCommandArgumentIfIsTag(
            context: CommandContext<ServerCommandSource>,
            functionArgumentName: String,
            pauseContext: PauseContext,
            macros: NbtCompound?,
            unpauseCallback: () -> Unit
        ) {
            val functionArgument = CommandFunctionArgumentType.getFunctionOrTag(context, functionArgumentName)
            if(functionArgument.second.right().isPresent)
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
        }

        fun <TSource> wrapCommandActionWithTagPauseCheck(action: CommandAction<TSource>, entryIndex: Int): CommandAction<TSource> {
            val pauseContext = PauseContext.currentPauseContext.get()
            val tagDebugFrame = (pauseContext?.peekDebugFrame() as? FunctionTagDebugFrame)
            return if(tagDebugFrame != null)
                CommandAction { context, frame ->
                    tagDebugFrame.checkPause(entryIndex)
                    action.execute(context, frame)
                }
            else
                action

        }
    }

    val filePath = PackContentFileType.FUNCTION_TAGS_FILE_TYPE.toStringPath(
        PackagedId(tagId.withExtension(FunctionTagDebugHandler.TAG_FILE_EXTENSION), "")
    )

    private var nextPauseIndex = -1
    private var lastPauseIndex = -1

    private val createdSourceReferences = Reference2IntOpenHashMap<EditorDebugConnection>()
    @Suppress("DEPRECATION")
    val currentSourceReference: Int?
        get() = createdSourceReferences[pauseContext.debugConnection!!]

    var sourceReferenceEntries: List<Pair<Identifier, Range>>? = null
    var sourceReferenceFileRange: Range? = null

    private var debugPauseHandler: DebugPauseHandler? = null

    var breakpoints: List<ServerBreakpoint<FunctionTagBreakpointLocation>>

    var currentEntryIndex = 0

    var accumulatedResult = false to 0

    fun addFunctionResult(result: CommandResult) {
        if(result.returnValue != null)
            // ReturnValueAdder in FunctionCommand always goes to successful=true as well
            accumulatedResult = true to (accumulatedResult.second + result.returnValue.second)
    }

    override fun onContinue(stackEntry: PauseContext.DebugFrameStack.Entry) {
        breakpoints = pauseContext.server.getDebugManager().functionTagDebugHandler.getTagBreakpoints(tagId, createdSourceReferences)
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
        val handler = FunctionTagDebugPauseHandler(this)
        debugPauseHandler = handler
        return handler
    }

    override fun unpause() {
        pauseContext.server.execute {
            pauseContext.executionWrapper.runCallback(unpauseCallback)
        }
    }

    override fun shouldWrapInSourceReference(path: String): Either<PauseContext.NewSourceReferenceWrapper, PauseContext.ExistingSourceReferenceWrapper>? {
        TODO("Not yet implemented")
    }

    override fun onExitFrame() {
        debugPauseHandler?.onExitFrame()
        val debugManager = pauseContext.server.getDebugManager()
        createdSourceReferences.forEach {
            debugManager.removeSourceReference(it.key, it.value)
        }
    }
}