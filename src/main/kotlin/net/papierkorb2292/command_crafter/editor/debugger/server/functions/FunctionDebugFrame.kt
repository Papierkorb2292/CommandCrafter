package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.ContextChain
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.Procedure
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionPausedThrowable
import net.papierkorb2292.command_crafter.editor.debugger.helper.ServerDebugManagerContainer
import net.papierkorb2292.command_crafter.editor.debugger.helper.copy
import net.papierkorb2292.command_crafter.editor.debugger.helper.get
import net.papierkorb2292.command_crafter.editor.debugger.server.FileContentReplacer
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.PositionableBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ContextChainAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.SingleCommandActionAccessor

class FunctionDebugFrame(
    val pauseContext: PauseContext,
    val procedure: Procedure<ServerCommandSource>,
    private val debugPauseHandlerFactory: FunctionDebugPauseHandlerFactory,
    val macroNames: List<String>,
    val macroArguments: List<String>,
    val unpauseCallback: () -> Unit,
    val fileLines: Map<String, List<String>>,
) : PauseContext.DebugFrame {
    @Suppress("UNCHECKED_CAST")
    val contextChains: List<ContextChain<ServerCommandSource>> =
        procedure.entries().mapNotNull {
            (it as? SingleCommandActionAccessor<ServerCommandSource>)?.contextChain
        }

    var currentCommandIndex = 0
    var currentSectionIndex = 0
    var sectionSources: MutableList<SectionSources> = mutableListOf()

    val currentContextChain: ContextChain<ServerCommandSource>
        get() = contextChains[currentCommandIndex]
    val currentContext: CommandContext<ServerCommandSource>
        get() = currentContextChain[currentSectionIndex]!!
    val currentSectionSources: SectionSources
        get() = sectionSources[currentSectionIndex]
    var currentSource: ServerCommandSource
        get() = currentSectionSources.currentSource
        set(value) {
            currentSectionSources.currentSource = value
        }

    var nextPauseRootContext: CommandContext<ServerCommandSource>? = null
    var nextPauseSectionIndex: Int = 0

    private var debugPauseHandler: DebugPauseHandler? = null
    override fun getDebugPauseHandler(): DebugPauseHandler {
        debugPauseHandler?.run { return this }
        val handler = debugPauseHandlerFactory.createDebugPauseHandler(this)
        debugPauseHandler = handler
        return handler
    }

    lateinit var breakpoints: List<ServerBreakpoint<FunctionBreakpointLocation>>

    private val reloadBreakpointsCallback = {
        val functionDebugHandler = (pauseContext.server as ServerDebugManagerContainer).`command_crafter$getServerDebugManager`().functionDebugHandler
        breakpoints = functionDebugHandler.getFunctionBreakpoints(procedure.id())
    }

    init {
        pauseContext.addOnContinueListener(reloadBreakpointsCallback)
        reloadBreakpointsCallback()
        if(breakpoints.isNotEmpty()) {
            getDebugPauseHandler()
        }
    }

    val executionWrapper = PauseContext.ExecutionWrapperConsumerImpl(pauseContext.server)

    fun getBreakpointsForCommand(commandRootContext: CommandContext<ServerCommandSource>): List<ServerBreakpoint<FunctionBreakpointLocation>> {
        return breakpoints.filter { it.action?.location?.commandLocationRoot == commandRootContext }
    }

    fun checkPause(commandInfo: CommandInfo, sectionIndex: Int, context: CommandContext<*>, source: ServerCommandSource) {
        currentSectionIndex = commandInfo.sectionOffset + sectionIndex
        if (pauseContext.isDebugging()) {
            if (nextPauseRootContext === contextChains[commandInfo.commandIndex].topContext && nextPauseSectionIndex == currentSectionIndex) {
                onReachedPauseLocation()
            }
        } else {
            for (breakpoint in commandInfo.breakpoint) {
                val action = breakpoint.action
                if (action != null && action.location.commandSectionLocation === context &&
                    (action.condition == null || action.condition.checkCondition(source) && action.condition.checkHitCondition(
                        source
                    ))
                ) {
                    onBreakpointHit(breakpoint)
                }
            }
        }
    }

    private var lastCommandInfoRequestedIndex = -1

    fun getCommandInfo(commandContext: CommandContext<ServerCommandSource>): CommandInfo? {
        val commands = contextChains.subList(currentCommandIndex, contextChains.size)
        for(i in commands.indices) {
            val topContext = commands[i].topContext
            var context: CommandContext<ServerCommandSource>? = topContext
            var sectionIndex = 0
            while(context != null) {
                if(context == commandContext) {
                    val commandIndex = i + currentCommandIndex
                    if(commandIndex != lastCommandInfoRequestedIndex) {
                        sectionSources.clear()
                        currentSectionIndex = 0
                        lastCommandInfoRequestedIndex = commandIndex
                    }
                    return CommandInfo(commandIndex, getBreakpointsForCommand(topContext), sectionIndex)
                }
                context = context.child
                sectionIndex++
            }
        }
        return null
    }

    fun pauseAtSection(rootContext: CommandContext<ServerCommandSource>, sectionIndex: Int) {
        nextPauseRootContext = rootContext
        nextPauseSectionIndex = sectionIndex
        pauseContext.unpause()
    }

    fun hasNextSection()
        = currentSectionIndex < (currentContextChain as ContextChainAccessor<*>).modifiers.size

    override fun unpause() {
        executionWrapper.runCallback(unpauseCallback)
    }

    override fun shouldWrapInSourceReference(path: String): PauseContext.SourceReferenceWrapper? {
        val pauseHandler = getDebugPauseHandler()
        if(pauseHandler !is FileContentReplacer) return null
        val lines = fileLines[path] ?: return null
        val editorConnection = pauseContext.editorConnection ?: return null
        val replacementData = pauseHandler.getReplacementData(path)
        if(replacementData == null || !replacementData.replacings.iterator().hasNext()) return null
        return PauseContext.SourceReferenceWrapper(replacementData.sourceReferenceCallback) { sourceReference ->
            val newBreakpoints = breakpoints.map {
                PositionableBreakpoint(it.unparsed.sourceBreakpoint.copy())
            }
            val replacedDocument = FileContentReplacer.Document(
                lines,
                newBreakpoints.asSequence() + replacementData.positionables
            ).applyReplacings(replacementData.replacings)
            (pauseContext.server as ServerDebugManagerContainer).`command_crafter$getServerDebugManager`().functionDebugHandler.addNewSourceReferenceBreakpoints(
                newBreakpoints.map { it.sourceBreakpoint },
                editorConnection,
                procedure.id(),
                sourceReference
            )
            replacedDocument.concatLines()
        }
    }

    override fun onExitFrame() {
        pauseContext.removeOnContinueListener(reloadBreakpointsCallback)
        debugPauseHandler?.onExitFrame()
    }

    fun onBreakpointHit(breakpoint: ServerBreakpoint<FunctionBreakpointLocation>) {
        if(pauseContext.initBreakpointPause(breakpoint)) {
            throw CommandExecutionPausedThrowable(executionWrapper)
        }
    }

    fun onReachedPauseLocation() {
        if(pauseContext.initPauseLocationReached()) {
            throw CommandExecutionPausedThrowable(executionWrapper)
        }
    }

    class SectionSources(val sources: MutableList<ServerCommandSource>, val parentSourceIndices: MutableList<Int>, var currentSourceIndex: Int) {
        fun hasNext(): Boolean = currentSourceIndex < sources.size - 1
        fun getNext(): ServerCommandSource? = if(hasNext()) sources[currentSourceIndex + 1] else null

        var currentSource: ServerCommandSource
            get() = sources[currentSourceIndex]
            set(value) {
                sources[currentSourceIndex] = value
            }
    }

    class CommandInfo(val commandIndex: Int, val breakpoint: List<ServerBreakpoint<FunctionBreakpointLocation>>, val sectionOffset: Int)
}