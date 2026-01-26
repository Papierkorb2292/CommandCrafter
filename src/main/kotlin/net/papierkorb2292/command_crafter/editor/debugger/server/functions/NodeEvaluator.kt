package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import com.mojang.datafixers.util.Either
import net.minecraft.ChatFormatting
import net.minecraft.commands.*
import net.minecraft.commands.arguments.*
import net.minecraft.commands.arguments.coordinates.*
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.commands.data.BlockDataAccessor
import net.minecraft.server.commands.data.DataAccessor
import net.minecraft.server.commands.data.EntityDataAccessor
import net.minecraft.server.commands.data.StorageDataAccessor
import net.minecraft.util.Mth
import net.minecraft.world.Container
import net.minecraft.world.entity.SlotProvider
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.scores.ScoreHolder
import net.papierkorb2292.command_crafter.editor.debugger.helper.EditorDebugConnection
import net.papierkorb2292.command_crafter.editor.debugger.helper.EvaluationProvider
import net.papierkorb2292.command_crafter.editor.debugger.helper.EvaluationProvider.Companion.withAlternativeForNull
import net.papierkorb2292.command_crafter.editor.debugger.helper.HoverCursorContainer
import net.papierkorb2292.command_crafter.editor.debugger.variables.*
import net.papierkorb2292.command_crafter.mixin.editor.processing.RecipeManagerAccessor
import net.papierkorb2292.command_crafter.mixin.parser.CommandNodeAccessor
import net.papierkorb2292.command_crafter.parser.helper.CursorOffsetContainer
import net.papierkorb2292.command_crafter.parser.helper.getCursorOffset
import org.eclipse.lsp4j.debug.*
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.math.absoluteValue

fun interface NodeEvaluator {
    fun getEvaluationProvider(
        argumentName: String,
        context: CommandContext<CommandSourceStack>,
        mapper: VariablesReferenceMapper,
        cursor: Int,
        includeInterpretation: Boolean,
    ): EvaluationProvider

    companion object {
        // Specified separately instead of in one dispatcher to better handle ambiguity
        private val evaluationParsers = listOf<EvaluationParser>(
            EvaluationParser({ server ->
                val registries = (server.recipeManager as RecipeManagerAccessor).registries // These registries contain loot data types
                val buildContext = CommandBuildContext.simple(registries, server.worldData.enabledFeatures())
                Commands.argument("predicate", ResourceOrIdArgument.lootPredicate(buildContext)).build()
            }),
            EvaluationParser({
                Commands.argument("position", Vec3Argument.vec3(true))
                    .then(Commands.argument("slots", SlotsArgument.slots()))
                    .then(Commands.argument("path", NbtPathArgument.nbtPath()))
                    .build()
            }),
            EvaluationParser({
                Commands.argument("rotation", RotationArgument.rotation()).build()
            }),
            EvaluationParser({
                Commands.argument("scoreHolder", ScoreHolderArgument.scoreHolders())
                    .then(Commands.argument("objective", ObjectiveArgument.objective()))
                    .build()
            }, { context, input ->
                // If objective arg doesn't exist and score holder could be an entity, it should be interpreted as an NBT path instead
                try {
                    EntityArgument.entities().parse(StringReader(input))
                    // Must be entity, since no exception was thrown
                    val name = context.arguments["objective"]?.result as? String ?: return@EvaluationParser false
                    context.source.server.scoreboard.getObjective(name) != null
                } catch(_: CommandSyntaxException) {
                    true
                }
            }),
            EvaluationParser({
                Commands.argument("storage", IdentifierArgument.id())
                    .then(Commands.argument("path", NbtPathArgument.nbtPath()))
                    .build()
            }, { context, input ->
                // Only valid if path was supplied and storage exists
                if(context.arguments["path"] == null)
                    return@EvaluationParser false
                val storageId = context.arguments["storage"]?.result as? Identifier ?: return@EvaluationParser false
                context.source.server.commandStorage.get(storageId).size() != 0
            }),
            EvaluationParser({
                Commands.argument("entity", EntityArgument.entities())
                    .then(Commands.argument("slots", SlotsArgument.slots()))
                    .then(Commands.argument("path", NbtPathArgument.nbtPath()))
                    .build()
            })
        )

        private fun tryParseExpression(input: String, source: CommandSourceStack): Either<CommandContextBuilder<CommandSourceStack>, CommandSyntaxException> {
            val errors = mutableListOf<CommandSyntaxException>()
            // Use the first valid result, which is validated by the parser to handle ambiguity
            val parsed = evaluationParsers.firstNotNullOfOrNull { parser ->
                val node = parser.parseTreeProvider(source.server)
                val dispatcher = CommandDispatcher(RootCommandNode<CommandSourceStack>().apply {
                    addChild(node)
                })
                val parseResults = dispatcher.parse(input, source)

                if(parseResults.exceptions.isNotEmpty()) {
                    errors += parseResults.exceptions.values.maxBy { it.cursor }
                    null
                } else if(parseResults.reader.canRead()) {
                    errors += CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parseResults.reader)
                    null
                } else if(parser.validator(parseResults.context, input)) {
                    parseResults
                } else {
                    null
                }
            } ?: return Either.right(errors.maxBy { it.cursor })
            return Either.left(parsed.context)
        }

        private fun findClosestArgumentName(
            startName: String,
            context: CommandContext<CommandSourceStack>,
            types: Set<Class<out ArgumentType<*>>>,
        ): ArgumentCommandNode<CommandSourceStack, *>? {
            val startIndex = context.nodes.indexOfFirst { it.node.name == startName }
            val entry = context.nodes.withIndex()
                .filter {
                    val argumentClass = (it.value.node as? ArgumentCommandNode<*, *>)?.type?.javaClass
                    (argumentClass as Class<out ArgumentType<*>>?) in types
                }
                .minByOrNull { (it.index - startIndex).absoluteValue }
                ?: return null
            return entry.value.node as ArgumentCommandNode<CommandSourceStack, *>
        }

        private fun getSlotProvidersFromContext(
            startName: String,
            context: CommandContext<CommandSourceStack>,
        ): List<SlotProvider> {
            val argument = findClosestArgumentName(
                startName,
                context,
                mutableSetOf(
                    EntityArgument::class.java,
                    BlockPosArgument::class.java,
                    Vec3Argument::class.java
                )
            ) ?: return emptyList()
            return when(argument.type) {
                is EntityArgument -> {
                    EntityArgument.getOptionalEntities(context, argument.name).toList()
                }
                is BlockPosArgument -> {
                    val pos = BlockPosArgument.getBlockPos(context, argument.name)
                    val blockEntity = context.source.level.getBlockEntity(pos)
                    if(blockEntity is Container) listOf(blockEntity) else emptyList()
                }
                is Vec3Argument -> {
                    val vec3 = Vec3Argument.getVec3(context, argument.name)
                    val blockEntity = context.source.level.getBlockEntity(BlockPos.containing(vec3))
                    if(blockEntity is Container) listOf(blockEntity) else emptyList()
                }
                else -> throw AssertionError("Unhandled argument type for SlotProvider evaluation: ${argument.type.javaClass}")
            }
        }

        private val ERROR_NOT_A_BLOCK_ENTITY = SimpleCommandExceptionType(Component.translatable("commands.data.block.invalid"))

        @Throws(CommandSyntaxException::class)
        private fun getDataAccessorFromContext(
            startName: String,
            context: CommandContext<CommandSourceStack>,
        ): DataAccessor? {
            val argument = findClosestArgumentName(
                startName,
                context,
                setOf(
                    EntityArgument::class.java,
                    BlockPosArgument::class.java,
                    Vec3Argument::class.java,
                    IdentifierArgument::class.java
                )
            ) ?: return null
            return when(argument.type) {
                is EntityArgument -> {
                    EntityDataAccessor.PROVIDER.apply(argument.name).access(context)
                }

                is BlockPosArgument -> {
                    val pos = BlockPosArgument.getBlockPos(context, argument.name)
                    val blockEntity = context.source.level.getBlockEntity(pos)
                        ?: throw ERROR_NOT_A_BLOCK_ENTITY.create()
                    BlockDataAccessor(blockEntity, pos)
                }

                is Vec3Argument -> {
                    val vec3 = Vec3Argument.getVec3(context, argument.name)
                    val pos = BlockPos.containing(vec3)
                    val blockEntity = context.source.level.getBlockEntity(pos)
                        ?: throw ERROR_NOT_A_BLOCK_ENTITY.create()
                    BlockDataAccessor(blockEntity, pos)
                }

                is IdentifierArgument -> {
                    StorageDataAccessor.PROVIDER.apply(argument.name).access(context)
                }

                else -> throw AssertionError("Unhandled argument type for NBT path evaluation: ${argument.type.javaClass}")
            }
        }

        private fun getValueReferenceEvaluation(valueReference: VariableValueReference, name: String, includeInterpretation: Boolean): CompletableFuture<EvaluationProvider.EvaluationResult?> =
            CompletableFuture.completedFuture(EvaluationProvider.createResponse(valueReference.getEvaluateResponse().apply {
                if(includeInterpretation)
                    this.result = "$name: " + this.result
            }))

        private val nodeEvaluators = mutableMapOf<Class<out ArgumentType<*>>, NodeEvaluator>(
            ResourceOrIdArgument.LootPredicateArgument::class.java to NodeEvaluator { argumentName, context, _, _, includeInterpretation ->
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
            EntityArgument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val entities = EntityArgument.getOptionalEntities(context, argumentName).toList()
                        val valueReference =
                            if(entities.size == 1) EntityValueReference(mapper, entities[0], context.source) { newEntity -> entities[0] }
                            else EntityListValueReference(mapper, entities, context.source)
                        return getValueReferenceEvaluation(valueReference, "Selector", includeInterpretation)
                    }
                }
            },
            ScoreHolderArgument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val objectiveArgument = findClosestArgumentName(argumentName, context, setOf(ObjectiveArgument::class.java))
                            ?: return CompletableFuture.completedFuture(null)
                        val objective = try {
                            ObjectiveArgument.getObjective(context, objectiveArgument.name)
                        } catch (e: CommandSyntaxException) {
                            return CompletableFuture.completedFuture(EvaluationProvider.createError(e.message!!))
                        }
                        val scoreHolders = ScoreHolderArgument.getNames(context, argumentName) {
                            context.source.server.scoreboard.listPlayerScores(objective).mapNotNull { scoreHolderFromName(it.owner, context.source) }
                        }.toList()
                        val valueReference =
                            if(scoreHolders.size == 1) ScoreHolderValueReference(mapper, scoreHolders[0], objective, context.source, includeName = true, allowEntityChild = true) { }
                            else ScoreHolderMapValueReference(mapper, scoreHolders, objective, context.source, compactEmptyScores = true) { }
                        return getValueReferenceEvaluation(valueReference, "Scores", includeInterpretation)
                    }
                }
            },
            GameProfileArgument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val entities = try {
                            GameProfileArgument.getGameProfiles(context, argumentName).mapNotNull {
                                context.source.server.playerList.getPlayer(it.id)
                            }
                        } catch (_: CommandSyntaxException) { listOf() }
                        val valueReference =
                            if(entities.size == 1) EntityValueReference(mapper, entities[0], context.source) { newEntity -> entities[0] }
                            else EntityListValueReference(mapper, entities, context.source)
                        return getValueReferenceEvaluation(valueReference, "Selector", includeInterpretation)
                    }
                }
            },
            Vec3Argument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val vec3 = Vec3Argument.getVec3(context, argumentName)
                        val valueReference = LevelCoordinateValueReference(mapper, vec3, context.source.level) { newVec3 -> newVec3 }
                        return getValueReferenceEvaluation(valueReference, "Position", includeInterpretation)
                    }
                }
            },
            BlockPosArgument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val pos = BlockPosArgument.getBlockPos(context, argumentName)
                        val valueReference = LevelCoordinateValueReference(mapper, Vec3.atLowerCornerOf(pos), context.source.level) { newVec3 -> newVec3 }
                        return getValueReferenceEvaluation(valueReference, "Position", includeInterpretation)
                    }
                }
            },
            ColumnPosArgument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val pos = ColumnPosArgument.getColumnPos(context, argumentName)
                        val vec = Vec2(pos.x.toFloat(), pos.z.toFloat())
                        val valueReference = Vec2fValueReference(mapper, vec, Vec2fValueReference.ComponentFormat.Column) { newVec2 -> newVec2 }
                        return getValueReferenceEvaluation(valueReference, "Position", includeInterpretation)
                    }
                }
            },
            RotationArgument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val vec2 = wrapDegrees(RotationArgument.getRotation(context, argumentName).getRotation(context.source))
                        val valueReference = Vec2fValueReference(mapper, vec2, Vec2fValueReference.ComponentFormat.Rotation) { newVec2 -> newVec2 }
                        return getValueReferenceEvaluation(valueReference, "Rotation", includeInterpretation)
                    }
                }
            },
            Vec2Argument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val vec2 = wrapDegrees(Vec2Argument.getVec2(context, argumentName))
                        val valueReference = Vec2fValueReference(mapper, vec2, Vec2fValueReference.ComponentFormat.Normal) { newVec2 -> newVec2 }
                        return getValueReferenceEvaluation(valueReference, "Rotation", includeInterpretation)
                    }
                }
            },
            SlotArgument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val range = context.nodes.first { it.node.name == argumentName }.range
                        val slotRange = SlotsArgument.slots().parse(StringReader(range.get(context.input)))
                        val providers = getSlotProvidersFromContext(argumentName, context)
                        val registries = context.source.server.registries().compositeAccess()
                        val valueReference =
                            if(providers.size == 1) SlotAccessValueReference(mapper, providers[0], slotRange.slots().getInt(0), true, registries)
                            else SlotProviderMapValueReference(mapper, providers, slotRange, registries)
                        return getValueReferenceEvaluation(valueReference, "Slots", includeInterpretation)
                    }
                }
            },
            SlotsArgument::class.java to NodeEvaluator { argumentName, context, mapper, _, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val slotRange = SlotsArgument.getSlots(context, argumentName)
                        val providers = getSlotProvidersFromContext(argumentName, context)
                        val registries = context.source.server.registries().compositeAccess()
                        val valueReference =
                            if(providers.size == 1 && slotRange.size() == 1) SlotAccessValueReference(mapper, providers[0], slotRange.slots().getInt(0), true, registries)
                            else if(providers.size == 1) SlotRangeMapValueReference(mapper, providers[0], slotRange, true, registries)
                            else SlotProviderMapValueReference(mapper, providers, slotRange, registries)
                        return getValueReferenceEvaluation(valueReference, "Slots", includeInterpretation)
                    }
                }
            },
            NbtPathArgument::class.java to NodeEvaluator { argumentName, context, mapper, cursor, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val parsedNode = context.nodes.first { it.node.name == argumentName }
                        val range = parsedNode.range
                        try {
                            val argumentParser = NbtPathArgument()
                            // Only parse up until the cursor
                            var mappedCursor = cursor - (parsedNode as CursorOffsetContainer).getCursorOffset()
                            // VSCode includes leading '.' when determining the range for hover, so skip it here
                            if(mappedCursor in context.input.indices && context.input[mappedCursor] == '.')
                                mappedCursor++
                            @Suppress("KotlinConstantConditions")
                            (argumentParser as HoverCursorContainer).`command_crafter$setHoverCursor`(mappedCursor - range.start)
                            val nbtPath = argumentParser.parse(StringReader(range.get(context.input)))
                            val dataAccessor = getDataAccessorFromContext(argumentName, context)
                                ?: return CompletableFuture.completedFuture(null)
                            val data = dataAccessor.data
                            val result = try {
                                nbtPath.get(data)
                            } catch(_: CommandSyntaxException) {
                                listOf() // Use empty list if no data was found
                            }
                            val valueReference = if(result.size == 1)
                                NbtValueReference(mapper, result[0]) { newNbt -> newNbt }
                            else
                                NbtPathResultValueReference(mapper, result) { newData -> newData }
                            return getValueReferenceEvaluation(valueReference, "NBT", includeInterpretation)
                        } catch(e: CommandSyntaxException) {
                            return CompletableFuture.completedFuture(EvaluationProvider.createError(e.message!!))
                        }
                    }
                }
            }
        )

        private fun wrapDegrees(rot: Vec2): Vec2 =
            Vec2(Mth.wrapDegrees(rot.x), Mth.wrapDegrees(rot.y))

        private fun scoreHolderFromName(name: String, source: CommandSourceStack): ScoreHolder? {
            return try {
                ScoreHolderArgument.scoreHolder().parse(StringReader(name))
                    .getNames(source, Collections::emptyList)
                    .firstOrNull()
            } catch (_: CommandSyntaxException) {
                null
            }
        }

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

        fun getParsingEvaluationProvider(source: CommandSourceStack, mapper: VariablesReferenceMapper, editorDebugConnection: EditorDebugConnection): EvaluationProvider {
            return object : EvaluationProvider {
                override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                    val input = args.expression
                    if(input.startsWith('/')) {
                        // Don't run commands for hover, because they can have side effects
                        if(args.context == EvaluateArgumentsContext.HOVER)
                            return CompletableFuture.completedFuture(null)
                        var commandResult = CommandResult(null)
                        source.server.commands.performPrefixedCommand(
                            source.withCallback { success, returnValue ->
                                commandResult = CommandResult(success to returnValue)
                            },
                            input
                        )
                        val valueReference = CommandResultValueReference(mapper, commandResult) { _ -> commandResult }
                        return CompletableFuture.completedFuture(EvaluationProvider.createResponse(valueReference.getEvaluateResponse()))
                    }

                    // Check if user might have tried to execute a command but forgot '/'
                    val firstArg = StringReader(input).readUnquotedString()
                    if(firstArg in (source.server.commands.dispatcher.root as CommandNodeAccessor).literals) {
                        editorDebugConnection.output(OutputEventArguments().apply {
                            category = OutputEventArgumentsCategory.CONSOLE
                            output = "If you want to run a command, prefix it with '/'"
                        })
                    }

                    val parsed = tryParseExpression(input, source)
                    return parsed.map({ context ->
                        val cursor =
                            if(context.nodes.firstOrNull()?.node?.name == "scoreHolder") 0 // Placed at beginning because the score holder is evaluated to compute the scores
                            else context.range.end
                        getContextEvaluationProvider(
                            context.build(input),
                            source,
                            cursor,
                            mapper,
                            true
                        ).evaluate(args)
                    }, { error ->
                        CompletableFuture.completedFuture(EvaluationProvider.createError(error.message!!))
                    })
                }
            }
        }

        fun getContextEvaluationProvider(
            command: CommandContext<CommandSourceStack>,
            source: CommandSourceStack,
            cursor: Int,
            mapper: VariablesReferenceMapper,
            includeInterpretation: Boolean,
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
                    nodeEvaluators[node.type.javaClass]?.getEvaluationProvider(
                        node.name,
                        context,
                        mapper,
                        cursor,
                        includeInterpretation
                    )
                } else null
        }

        fun getEvaluationCommandSourceForConnection(editorDebugConnection: EditorDebugConnection): CommandSource {
            return object : CommandSource {
                override fun sendSystemMessage(component: Component) {
                    val isError = component.style.color == TextColor.fromLegacyFormat(ChatFormatting.RED)
                    editorDebugConnection.output(OutputEventArguments().apply {
                        category = if(isError) OutputEventArgumentsCategory.STDERR else OutputEventArgumentsCategory.STDOUT
                        output = component.string
                    })
                }

                override fun acceptsSuccess() = true
                override fun acceptsFailure() = true
                override fun shouldInformAdmins() = true

            }
        }
    }

    data class EvaluationParser(val parseTreeProvider: (MinecraftServer) -> CommandNode<CommandSourceStack>, val validator: (CommandContextBuilder<CommandSourceStack>, String) -> Boolean = { _, _ -> true })
}