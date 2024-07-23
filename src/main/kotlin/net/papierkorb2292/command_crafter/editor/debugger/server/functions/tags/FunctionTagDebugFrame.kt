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
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.mixin.editor.debugger.MacroAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ReturnValueAdderAccessor
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

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
                    FunctionDebugFrame.commandResult.remove()
                    tagDebugFrame.checkPause(entryIndex)
                    action.execute(context, frame)
                }
            else
                action
        }

        val LAST_TAG_PAUSE_COMMAND_ACTION = CommandAction<ServerCommandSource> { _, _ ->
            val pauseContext = PauseContext.currentPauseContext.getOrNull() ?: return@CommandAction
            val tagDebugFrame = (pauseContext.peekDebugFrame() as? FunctionTagDebugFrame) ?: return@CommandAction
            tagDebugFrame.checkPause(tagDebugFrame.currentEntryIndex + 1)
        }
        val COPY_TAG_RESULT_TO_COMMAND_RESULT_COMMAND_ACTION = CommandAction<ServerCommandSource> { _, _ ->
            val pauseContext = PauseContext.currentPauseContext.getOrNull() ?: return@CommandAction
            val tagDebugFrame = (pauseContext.peekDebugFrame() as? FunctionTagDebugFrame) ?: return@CommandAction
            FunctionDebugFrame.commandResult.set(CommandResult(tagDebugFrame.accumulatedResult))
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
        if(path != filePath) return null
        if(currentSourceReference != null) return Either.right(PauseContext.ExistingSourceReferenceWrapper(currentSourceReference!!, { }, false))
        return Either.left(PauseContext.NewSourceReferenceWrapper({
            createdSourceReferences[pauseContext.debugConnection!!] = it
        }, ) {
            val entryIds = pauseContext.server.commandFunctionManager.getTag(tagId).map { it.id() }
            val entryRanges = mutableListOf<Pair<Identifier, Range>>()
            sourceReferenceEntries = entryRanges
            val listIndent = "    "
            val entryIndent = listIndent.repeat(2)
            buildString {
                append("{\n")
                append("$listIndent\"values\": [\n")
                for((index, entry) in entryIds.withIndex()) {
                    append(entryIndent)
                    val entryString = "\"$entry\""
                    append(entryString)
                    entryRanges.add(entry to Range(
                        Position(2 + index + 1, entryIndent.length + 1),
                        Position(2 + index + 1, entryIndent.length + entryString.length + 1)
                    ))
                    if(index != entryIds.size - 1)
                        append(',')
                    append('\n')
                }
                append("$listIndent]\n")
                append("}")
            }
        })
    }

    override fun onExitFrame() {
        debugPauseHandler?.onExitFrame()
        val debugManager = pauseContext.server.getDebugManager()
        createdSourceReferences.forEach {
            debugManager.removeSourceReference(it.key, it.value)
        }
    }
}