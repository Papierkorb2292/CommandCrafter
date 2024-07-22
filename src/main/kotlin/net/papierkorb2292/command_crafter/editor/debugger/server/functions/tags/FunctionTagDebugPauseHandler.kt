package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebuggerVisualContext
import net.papierkorb2292.command_crafter.editor.debugger.helper.MinecraftStackFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.StepInTargetsManager
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResult
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResultValueReference
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ServerCommandSourceValueReference
import net.papierkorb2292.command_crafter.editor.debugger.variables.StringMapValueReference
import net.papierkorb2292.command_crafter.editor.debugger.variables.createScope
import net.papierkorb2292.command_crafter.editor.processing.helper.advance
import net.papierkorb2292.command_crafter.helper.arrayOfNotNull
import net.papierkorb2292.command_crafter.helper.getOrNull
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.StepInTarget
import org.eclipse.lsp4j.debug.StepInTargetsResponse
import org.eclipse.lsp4j.debug.SteppingGranularity
import java.util.concurrent.CompletableFuture

class FunctionTagDebugPauseHandler(val debugFrame: FunctionTagDebugFrame) : DebugPauseHandler {
    companion object {
        private const val COMMAND_SOURCE_SCOPE_NAME = "Command-Source"
        private const val FUNCTION_TAG_MACROS_SCOPE_NAME = "Macros"
        private const val FUNCTION_TAG_RESULT_SCOPE_NAME = "Command-Result"
    }

    override fun findNextPauseLocation() {
        debugFrame.pauseOnEntryIndex(debugFrame.currentEntryIndex + 1)
    }

    override fun getStackFrames(): List<MinecraftStackFrame> {
        val sourceReference = debugFrame.currentSourceReference
        val source = Source().apply {
            name = FunctionTagDebugHandler.getSourceName(debugFrame.tagId.toString(), sourceReference)
            path = debugFrame.filePath
        }

        if(sourceReference == INITIAL_SOURCE_REFERENCE) {
            // Return dummy frame until source reference is created
            return listOf(MinecraftStackFrame(
                "", DebuggerVisualContext(source, Range()), emptyArray()
            ))
        }

        val variablesReferenceMapper = debugFrame.pauseContext.variablesReferenceMapper
        val serverCommandSourceScope = ServerCommandSourceValueReference(
            variablesReferenceMapper,
            debugFrame.commandSource
        ).createScope(COMMAND_SOURCE_SCOPE_NAME)

        val lastFunctionResult = FunctionDebugFrame.commandResult.getOrNull() ?: CommandResult(null)
        val commandResultScope = TagResultValueReference(
            variablesReferenceMapper,
            CommandResultValueReference(variablesReferenceMapper, lastFunctionResult) { lastFunctionResult},
            CommandResultValueReference(variablesReferenceMapper, CommandResult(debugFrame.accumulatedResult)) {
                if(it.returnValue != null) {
                    debugFrame.setAccumulatedResult(it.returnValue.first, it.returnValue.second)
                    it
                } else CommandResult(debugFrame.accumulatedResult)
            }
        ).createScope(FUNCTION_TAG_RESULT_SCOPE_NAME)

        val macrosScope =
            if(debugFrame.macroArguments.isNotEmpty())
                StringMapValueReference(
                    debugFrame.pauseContext.variablesReferenceMapper,
                    debugFrame.macroNames.zip(debugFrame.macroArguments).toMap()
                ).createScope(FUNCTION_TAG_MACROS_SCOPE_NAME)
            else
                null

        val variableScopes = arrayOfNotNull(serverCommandSourceScope, commandResultScope, macrosScope)

        val hasRunThrough = debugFrame.sourceReferenceEntries!!.size <= debugFrame.currentEntryIndex
        val fileRange = debugFrame.sourceReferenceFileRange!!

        val result = mutableListOf(
            MinecraftStackFrame(
                '#' + debugFrame.tagId.toString(),
                DebuggerVisualContext(source, fileRange),
                variableScopes
            )
        )

        result += if(hasRunThrough) {
            MinecraftStackFrame(
                "return",
                DebuggerVisualContext(source, Range(fileRange.end.advance(-1), fileRange.end)),
                variableScopes
            )
        } else {
            val currentEntry = debugFrame.sourceReferenceEntries!![debugFrame.currentEntryIndex]
            val currentEntryId = currentEntry.first
            val currentEntryRange = currentEntry.second

            MinecraftStackFrame(
                currentEntryId.toString(),
                DebuggerVisualContext(source, currentEntryRange),
                variableScopes
            )
        }

        return result
    }

    override fun onExitFrame() { }

    override fun next(granularity: SteppingGranularity) {
        debugFrame.pauseOnEntryIndex(debugFrame.currentEntryIndex + 1)
    }

    override fun stepIn(granularity: SteppingGranularity, targetId: Int?) {
        debugFrame.pauseContext.stepIntoFrame()
    }

    override fun stepOut(granularity: SteppingGranularity) {
        debugFrame.pauseContext.pauseAfterExitFrame()
    }

    override fun stepInTargets(frameId: Int): CompletableFuture<StepInTargetsResponse> =
        CompletableFuture.completedFuture(StepInTargetsResponse().apply {
            targets = arrayOf(StepInTarget().apply {
                id = debugFrame.pauseContext.stepInTargetsManager.addStepInTarget(StepInTargetsManager.Target {
                    debugFrame.pauseContext.stepIntoFrame()
                })
                val currentFunctionId = debugFrame.pauseContext.server.commandFunctionManager
                    .getTag(debugFrame.tagId)!!
                    .elementAt(debugFrame.currentEntryIndex)
                label = "Step into function '$currentFunctionId'"
            })
        })

    override fun continue_() {
        debugFrame.pauseContext.removePause()
    }
}