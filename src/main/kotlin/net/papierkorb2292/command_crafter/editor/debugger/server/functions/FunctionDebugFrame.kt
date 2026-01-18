package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.ContextChain
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import com.mojang.datafixers.util.Either
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.ResourceOrIdArgument
import net.minecraft.commands.functions.InstantiatedFunction
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandler
import net.papierkorb2292.command_crafter.editor.debugger.helper.*
import net.papierkorb2292.command_crafter.editor.debugger.helper.EvaluationProvider.Companion.withAlternativeForNull
import net.papierkorb2292.command_crafter.editor.debugger.server.FileContentReplacer
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext.Companion.currentPauseContext
import net.papierkorb2292.command_crafter.editor.debugger.server.ServerDebugManager.Companion.INITIAL_SOURCE_REFERENCE
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.BreakpointManager
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.PositionableBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint
import net.papierkorb2292.command_crafter.editor.debugger.variables.EntityListValueReference
import net.papierkorb2292.command_crafter.editor.debugger.variables.EntityValueReference
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferenceMapper
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.mixin.editor.debugger.BuildContextsAccessor
import net.papierkorb2292.command_crafter.mixin.editor.debugger.ContextChainAccessor
import net.papierkorb2292.command_crafter.mixin.editor.processing.RecipeManagerAccessor
import net.papierkorb2292.command_crafter.parser.helper.CursorOffsetContainer
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper
import net.papierkorb2292.command_crafter.parser.helper.getCursorOffset
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory
import java.util.*
import java.util.concurrent.CompletableFuture

class FunctionDebugFrame(
    val pauseContext: PauseContext,
    val procedure: InstantiatedFunction<CommandSourceStack>,
    private val debugPauseHandlerFactory: FunctionDebugPauseHandlerFactory,
    val macroNames: List<String>,
    val macroArguments: List<String>,
    val unpauseCallback: () -> Unit,
    val sourceFileId: Identifier,
    val functionLines: List<String>,
) : PauseContext.DebugFrame, CommandFeedbackConsumer {
    companion object {
        val sourceReferenceCursorMapper = mutableMapOf<Pair<EditorDebugConnection, Int>, ProcessedInputCursorMapper>()
        fun getCommandInfo(context: CommandContext<CommandSourceStack>): CommandInfo? {
            val pauseContext = currentPauseContext.get() ?: return null
            val debugFrame = pauseContext.peekDebugFrame() as? FunctionDebugFrame ?: return null
            return debugFrame.getCommandInfo(context)
        }

        fun checkSimpleActionPause(context: CommandContext<CommandSourceStack>, source: CommandSourceStack, commandInfo: CommandInfo? = null) {
            val pauseContext = currentPauseContext.get() ?: return
            val debugFrame = pauseContext.peekDebugFrame() as? FunctionDebugFrame ?: return
            val resolvedCommandInfo = commandInfo ?: debugFrame.getCommandInfo(context) ?: return
            debugFrame.currentSectionIndex = resolvedCommandInfo.sectionOffset
            debugFrame.checkPause(
                resolvedCommandInfo,
                context,
                source
            )
            val sectionSources = debugFrame.currentSectionSources
            sectionSources.currentSourceIndex += 1
        }

        //TODO: Add other arguments
        private fun getEvaluationDispatcher(server: MinecraftServer) = CommandDispatcher(RootCommandNode<CommandSourceStack>().apply {
            val registries = (server.recipeManager as RecipeManagerAccessor).registries // These registries contain loot data types
            val buildContext = CommandBuildContext.simple(registries, server.worldData.enabledFeatures())
            addChild(Commands.argument("predicate", ResourceOrIdArgument.lootPredicate(buildContext)).build())
            addChild(Commands.argument("entity", EntityArgument.entities()).build())
        })

        private val nodeEvaluators = mutableMapOf<Class<out ArgumentType<*>>, NodeEvaluator>(
            ResourceOrIdArgument.LootPredicateArgument::class.java to NodeEvaluator { argumentName, context, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val value = ResourceOrIdArgument.getLootPredicate(context, argumentName).value()
                        val source = context.source
                        val serverLevel = source.level
                        val lootParams = LootParams.Builder(serverLevel)
                            .withParameter(LootContextParams.ORIGIN, source.position)
                            .withOptionalParameter(LootContextParams.THIS_ENTITY, source.entity)
                            .create(LootContextParamSets.COMMAND)
                        val lootContext = LootContext.Builder(lootParams).create(Optional.empty())
                        lootContext.pushVisitedElement(LootContext.createVisitedEntry(value))
                        val result = value.test(lootContext)
                        return CompletableFuture.completedFuture(EvaluationProvider.createResponse(EvaluateResponse().apply {
                            this.result = result.toString()
                            if(includeInterpretation)
                                this.result = "Predicate: " + this.result
                        }))
                    }
                }
            },
            EntityArgument::class.java to NodeEvaluator { argumentName, context, mapper, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val entities = EntityArgument.getOptionalEntities(context, argumentName).toList()
                        val valueReference =
                            if(entities.size == 1) EntityValueReference(mapper, entities[0], context.source) { newEntity -> entities[0] }
                            else EntityListValueReference(mapper, entities, context.source)
                        return CompletableFuture.completedFuture(EvaluationProvider.createResponse(valueReference.getEvaluateResponse().apply {
                            if(includeInterpretation)
                                this.result = "Selector: " + this.result
                        }))
                    }
                }
            }
        )

        private val evaluatableExecuteConditions = setOf("block", "biome", "loaded", "dimension", "score", "blocks", "entity", "predicate", "items", "stopwatch")

        private fun getExecuteConditionEvaluationProvider(subcontext: CommandContext<CommandSourceStack>, mapper: VariablesReferenceMapper) = object : EvaluationProvider {
            override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                val dispatcher = subcontext.source.server.commands.dispatcher
                val ifNode = dispatcher.findNode(listOf("execute", "if"))
                val unlessNode = dispatcher.findNode(listOf("execute", "unless"))
                val conditionIndex = subcontext.nodes.indexOfFirst {
                    it.node === ifNode || it.node === unlessNode
                }
                if(conditionIndex == -1 || conditionIndex + 1 >= subcontext.nodes.size)
                    return CompletableFuture.completedFuture(null)

                val conditionNode = subcontext.nodes[conditionIndex + 1].node
                if(conditionNode !is LiteralCommandNode<*> || conditionNode.literal !in evaluatableExecuteConditions)
                    return CompletableFuture.completedFuture(null)

                val result = try {
                    CommandResult(true to subcontext.command.run(subcontext))
                } catch(_: CommandSyntaxException) {
                    CommandResult(false to 0)
                }

                val valueReference = CommandResultValueReference(mapper, result) { newResult -> result }

                return CompletableFuture.completedFuture(EvaluationProvider.createResponse(valueReference.getEvaluateResponse()))
            }
        }

        //TODO: Execute commands if starts with '/' (only if allowed: check context)
        fun getParsingEvaluationProvider(source: CommandSourceStack, mapper: VariablesReferenceMapper): EvaluationProvider {
            return object : EvaluationProvider {
                override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                    val input = args.expression
                    val parseResults = getEvaluationDispatcher(source.server).parse(input, source)
                    if(parseResults.exceptions.isNotEmpty())
                        return CompletableFuture.completedFuture(EvaluationProvider.createError(
                            parseResults.exceptions.values.maxBy { it.cursor }.message!!
                        ))
                    if(parseResults.reader.canRead())
                        return CompletableFuture.completedFuture(EvaluationProvider.createError(
                            CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parseResults.reader).message!!
                        ))
                    return getContextEvaluationProvider(
                        parseResults.context.build(input),
                        source,
                        parseResults.reader.cursor,
                        mapper,
                        true
                    ).evaluate(args)
                }
            }
        }

        fun getContextEvaluationProvider(
            command: CommandContext<CommandSourceStack>,
            source: CommandSourceStack,
            cursor: Int,
            mapper: VariablesReferenceMapper,
            includeInterpretation: Boolean
        ) = object : EvaluationProvider {
            override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                // Get the node at the cursor
                var context: CommandContext<CommandSourceStack>? = command
                while(context != null) {
                    if(context.nodes.isNotEmpty()) {
                        if(cursor <= context.range.end + (context.nodes.last() as CursorOffsetContainer).getCursorOffset()) {
                            // Found the right context
                            break
                        }
                    }
                    context = context.child
                }
                val node = context?.nodes?.firstOrNull { node ->
                    cursor <= node.range.end + (node as CursorOffsetContainer).getCursorOffset()
                }?.node ?: return CompletableFuture.completedFuture(null)
                val contextWithSource = context.copyFor(source)
                val nodeEvaluator = getNodeEvaluator(node, contextWithSource)
                    .withAlternativeForNull(getExecuteConditionEvaluationProvider(contextWithSource, mapper))

                return nodeEvaluator.evaluate(args)
            }

            private fun getNodeEvaluator(node: CommandNode<CommandSourceStack>, context: CommandContext<CommandSourceStack>): EvaluationProvider? =
                if(node is ArgumentCommandNode<*, *>) {
                    nodeEvaluators[node.type.javaClass]?.getEvaluationProvider(node.name, context, mapper, includeInterpretation)
                } else null
        }
    }

    @Suppress("UNCHECKED_CAST")
    val contextChains: List<ContextChain<CommandSourceStack>> =
        procedure.entries().mapNotNull {
            (it as? BuildContextsAccessor<CommandSourceStack>)?.command
        }

    var commandFeedbackConsumer: CommandFeedbackConsumer? = null

    var currentCommandIndex = 0

    var currentSectionIndex = 0
    var sectionSources: MutableList<SectionSources> = mutableListOf()
    val currentContextChain: ContextChain<CommandSourceStack>
        get() = contextChains[currentCommandIndex]

    val currentContext: CommandContext<CommandSourceStack>
        get() = currentContextChain[currentSectionIndex]!!
    val currentSectionSources: SectionSources
        get() = sectionSources[currentSectionIndex]
    var currentSource: CommandSourceStack
        get() = currentSectionSources.currentSource
        set(value) {
            currentSectionSources.currentSource = value
        }

    private val sourceFilePackagedId = PackagedId(sourceFileId, "")
    private val sourceFilePackagedIdWithoutExtension = sourceFilePackagedId.removeExtension(FunctionDebugHandler.FUNCTION_FILE_EXTENSTION) ?: throw IllegalArgumentException("Source file id $sourceFileId doesn't have .mcfunction extension")
    private val sourceFilePath = PackContentFileType.FUNCTIONS_FILE_TYPE.toStringPath(sourceFilePackagedId)

    private var nextPauseRootContext: CommandContext<CommandSourceStack>? = null
    private var nextPauseSectionIndex: Int = 0

    private var lastPauseContext: CommandContext<CommandSourceStack>? = null
    private var lastPauseSourceIndex: Int = 0

    private val createdSourceReferences = Reference2IntOpenHashMap<EditorDebugConnection>()
    @Suppress("DEPRECATION")
    val currentSourceReference: Int?
        get() = createdSourceReferences[pauseContext.debugConnection!!]
    val currentSourceReferenceCursorMapper: ProcessedInputCursorMapper?
        get() = sourceReferenceCursorMapper[pauseContext.debugConnection!! to currentSourceReference]

    private var debugPauseHandler: DebugPauseHandler? = null
    override fun getDebugPauseHandler(): DebugPauseHandler {
        debugPauseHandler?.run { return this }
        val handler = debugPauseHandlerFactory.createDebugPauseHandler(this)
        debugPauseHandler = handler
        return handler
    }

    var breakpoints: List<ServerBreakpoint<FunctionBreakpointLocation>>

    override fun onContinue(stackEntry: PauseContext.DebugFrameStack.Entry) {
        breakpoints = pauseContext.server.getDebugManager().functionDebugHandler.getFunctionBreakpoints(procedure.getOriginalId(), createdSourceReferences)
    }

    init {
        breakpoints = pauseContext.server.getDebugManager().functionDebugHandler.getFunctionBreakpoints(procedure.getOriginalId())
        if(breakpoints.isNotEmpty()) {
            // The debug pause handler might need to parse dynamic breakpoints
            getDebugPauseHandler()
        }
    }

    fun getBreakpointsForCommand(commandRootContext: CommandContext<CommandSourceStack>): List<ServerBreakpoint<FunctionBreakpointLocation>> {
        return breakpoints.filter { it.action?.location?.commandLocationRoot == commandRootContext }
    }

    fun checkPause(commandInfo: CommandInfo, context: CommandContext<*>, source: CommandSourceStack) {
        currentCommandIndex = commandInfo.commandIndex
        if(lastPauseContext === context && lastPauseSourceIndex == currentSectionSources.currentSourceIndex)
            return
        if(pauseContext.isDebugging()) {
            if(nextPauseRootContext === contextChains[commandInfo.commandIndex].topContext && nextPauseSectionIndex <= currentSectionIndex) {
                if(onReachedPauseLocation())
                    return
            }
            if(commandInfo.commandIndex > 0 && nextPauseRootContext === contextChains[commandInfo.commandIndex - 1].topContext) {
                nextPauseRootContext = null
                pauseContext.notifyClientPauseLocationSkipped()
                getDebugPauseHandler().findNextPauseLocation()
                checkPause(commandInfo, context, source)
                return
            }
        }
        for(breakpoint in commandInfo.breakpoints) {
            val action = breakpoint.action
            if(action != null && action.location.commandSectionLocation === context &&
                (action.condition == null || action.condition.checkCondition(source) && action.condition.checkHitCondition(
                    source
                ))
            ) {
                if(onBreakpointHit(breakpoint))
                    return
            }
        }
    }

    private var lastCommandInfoRequestedIndex = -1

    fun getCommandInfo(commandContext: CommandContext<CommandSourceStack>): CommandInfo? {
        val commands = contextChains.subList(currentCommandIndex, contextChains.size)
        for(i in commands.indices) {
            val topContext = commands[i].topContext
            var context: CommandContext<CommandSourceStack>? = topContext
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

    fun pauseAtSection(rootContext: CommandContext<CommandSourceStack>, sectionIndex: Int) {
        nextPauseRootContext = rootContext
        nextPauseSectionIndex = sectionIndex
        pauseContext.unpause()
    }

    fun hasNextSection()
        = currentSectionIndex < (currentContextChain as ContextChainAccessor<*>).modifiers.size

    override fun unpause() {
        pauseContext.server.execute {
            pauseContext.executionWrapper.runCallback(unpauseCallback)
        }
    }

    override fun shouldWrapInSourceReference(path: String): Either<PauseContext.NewSourceReferenceWrapper, PauseContext.ExistingSourceReferenceWrapper>? {
        if(!path.endsWith(sourceFilePath)) return null
        val existingSourceReference = currentSourceReference
        if(existingSourceReference != INITIAL_SOURCE_REFERENCE)
            return Either.right(PauseContext.ExistingSourceReferenceWrapper(existingSourceReference, {
                it.path = sourceFilePath
                it.name += "@$existingSourceReference"
            }, false))
        val pauseHandler = getDebugPauseHandler()
        if(pauseHandler !is FileContentReplacer) return null
        val editorConnection = pauseContext.debugConnection ?: return null
        val replacementData = pauseHandler.getReplacementData(path)
        if(replacementData == null || !replacementData.replacements.iterator().hasNext()) return null
        return Either.left(PauseContext.NewSourceReferenceWrapper({
            createdSourceReferences[editorConnection] = it
            replacementData.sourceReferenceCallback(it)
        }) { sourceReference ->
            val newBreakpoints = pauseContext.server.getDebugManager().functionDebugHandler
                .getFunctionBreakpointsForDebugConnection(procedure.getOriginalId(), editorConnection).map {
                    PositionableBreakpoint(it.unparsed.copy())
                }
            val (replacedDocument, cursorMapper) = FileContentReplacer.Document(
                functionLines,
                newBreakpoints.asSequence() + replacementData.positionables
            ).applyReplacements(replacementData.replacements)
            sourceReferenceCursorMapper[editorConnection to sourceReference] = cursorMapper
            pauseContext.server.getDebugManager().functionDebugHandler.addNewSourceReferenceBreakpoints(
                newBreakpoints.map { BreakpointManager.NewSourceReferenceBreakpoint(it.breakpoint.sourceBreakpoint, it.breakpoint.id) },
                editorConnection,
                sourceFilePackagedIdWithoutExtension,
                sourceReference
            )
            replacedDocument.concatLines()
        })
    }

    override fun onExitFrame() {
        debugPauseHandler?.onExitFrame()
        val debugManager = pauseContext.server.getDebugManager()
        createdSourceReferences.forEach {
            debugManager.removeSourceReference(it.key, it.value)
            sourceReferenceCursorMapper.remove(it.key to it.value)
        }
        if(pauseContext.debugFrameDepth == 0 && pauseContext.oneTimeDebugConnection != null) {
            pauseContext.oneTimeDebugConnection.output(OutputEventArguments().apply {
                category = OutputEventArgumentsCategory.IMPORTANT
                val commandResult = pauseContext.commandResult
                output = if(commandResult == null) {
                    "No return information available"
                } else {
                    val returnValue = commandResult.returnValue
                    if(returnValue == null) {
                        "Function didn't return a value"
                    } else {
                        "Function returned ${if(returnValue.first) "successfully" else "unsuccessfully"} with value ${returnValue.second}"
                    }
                }
            })
        }
    }
    
    private fun startPause() {
        lastPauseContext = currentContext
        lastPauseSourceIndex = currentSectionSources.currentSourceIndex
        nextPauseRootContext = null
        pauseContext.suspend() { CommandExecutionPausedThrowable(pauseContext.executionWrapper) }
    }

    fun resetLastPause() {
        lastPauseContext = null
    }

    fun onBreakpointHit(breakpoint: ServerBreakpoint<FunctionBreakpointLocation>): Boolean {
        if(breakpoint.unparsed.sourceReference == INITIAL_SOURCE_REFERENCE && breakpoint.debugConnection in createdSourceReferences.keys) {
            //This breakpoint shouldn't be paused at, because it doesn't belong to the debugee's sourceReference
            return false
        }
        if(pauseContext.initBreakpointPause(breakpoint)) {
            startPause()
            return true
        }
        return false
    }

    fun onReachedPauseLocation(): Boolean {
        if(pauseContext.initPauseLocationReached()) {
            startPause()
            return true
        }
        return false
    }

    class SectionSources(val sources: MutableList<CommandSourceStack>, val parentSourceIndices: MutableList<Int>, var currentSourceIndex: Int) {
        fun hasCurrent(): Boolean = currentSourceIndex < sources.size
        fun hasNext(): Boolean = currentSourceIndex < sources.size - 1
        fun getNext(): CommandSourceStack? = if(hasNext()) sources[currentSourceIndex + 1] else null

        var currentSource: CommandSourceStack
            get() = sources[currentSourceIndex]
            set(value) {
                sources[currentSourceIndex] = value
            }
    }

    class CommandInfo(val commandIndex: Int, val breakpoints: List<ServerBreakpoint<FunctionBreakpointLocation>>, val sectionOffset: Int)

    override fun onCommandFeedback(feedback: String) {
        commandFeedbackConsumer?.onCommandFeedback(feedback)
    }

    override fun onCommandError(error: String) {
        commandFeedbackConsumer?.onCommandError(error)
    }

    fun interface NodeEvaluator {
        fun getEvaluationProvider(
            argumentName: String,
            context: CommandContext<CommandSourceStack>,
            mapper: VariablesReferenceMapper,
            includeInterpretation: Boolean
        ): EvaluationProvider
    }
}