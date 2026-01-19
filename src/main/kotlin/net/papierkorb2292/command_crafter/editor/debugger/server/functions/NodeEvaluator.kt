package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import com.mojang.brigadier.tree.RootCommandNode
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.commands.arguments.ResourceOrIdArgument
import net.minecraft.commands.arguments.coordinates.*
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import net.minecraft.world.level.storage.loot.parameters.LootContextParams
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.papierkorb2292.command_crafter.editor.debugger.helper.EvaluationProvider
import net.papierkorb2292.command_crafter.editor.debugger.helper.EvaluationProvider.Companion.withAlternativeForNull
import net.papierkorb2292.command_crafter.editor.debugger.variables.*
import net.papierkorb2292.command_crafter.mixin.editor.processing.RecipeManagerAccessor
import net.papierkorb2292.command_crafter.parser.helper.CursorOffsetContainer
import net.papierkorb2292.command_crafter.parser.helper.getCursorOffset
import org.eclipse.lsp4j.debug.EvaluateArguments
import org.eclipse.lsp4j.debug.EvaluateResponse
import java.util.*
import java.util.concurrent.CompletableFuture

fun interface NodeEvaluator {
    fun getEvaluationProvider(
        argumentName: String,
        context: CommandContext<CommandSourceStack>,
        mapper: VariablesReferenceMapper,
        includeInterpretation: Boolean
    ): EvaluationProvider

    companion object {
        //TODO: Add other arguments (nbt paths, scores, slots)
        private fun getEvaluationDispatcher(server: MinecraftServer) = CommandDispatcher(RootCommandNode<CommandSourceStack>().apply {
            val registries = (server.recipeManager as RecipeManagerAccessor).registries // These registries contain loot data types
            val buildContext = CommandBuildContext.simple(registries, server.worldData.enabledFeatures())
            addChild(Commands.argument("predicate", ResourceOrIdArgument.lootPredicate(buildContext)).build())
            addChild(Commands.argument("entity", EntityArgument.entities()).build())
            addChild(Commands.argument("position", Vec3Argument.vec3(true)).build())
            addChild(Commands.argument("rotation", RotationArgument.rotation()).build())
        })

        private fun getValueReferenceEvaluation(valueReference: VariableValueReference, name: String, includeInterpretation: Boolean): CompletableFuture<EvaluationProvider.EvaluationResult?> =
            CompletableFuture.completedFuture(EvaluationProvider.createResponse(valueReference.getEvaluateResponse().apply {
                if(includeInterpretation)
                    this.result = "$name: " + this.result
            }))

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
                        return getValueReferenceEvaluation(valueReference, "Selector", includeInterpretation)
                    }
                }
            },
            GameProfileArgument::class.java to NodeEvaluator { argumentName, context, mapper, includeInterpretation ->
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
            Vec3Argument::class.java to NodeEvaluator { argumentName, context, mapper, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val vec3 = Vec3Argument.getVec3(context, argumentName)
                        val valueReference = Vec3dValueReference(mapper, vec3) { newVec3 -> newVec3 }
                        return getValueReferenceEvaluation(valueReference, "Position", includeInterpretation)
                    }
                }
            },
            BlockPosArgument::class.java to NodeEvaluator { argumentName, context, mapper, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val pos = BlockPosArgument.getBlockPos(context, argumentName)
                        val valueReference = Vec3dValueReference(mapper, Vec3.atLowerCornerOf(pos)) { newVec3 -> newVec3 }
                        return getValueReferenceEvaluation(valueReference, "Position", includeInterpretation)
                    }
                }
            },
            ColumnPosArgument::class.java to NodeEvaluator { argumentName, context, mapper, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val pos = ColumnPosArgument.getColumnPos(context, argumentName)
                        val vec = Vec2(pos.x.toFloat(), pos.z.toFloat())
                        val valueReference = Vec2fValueReference(mapper, vec, Vec2fValueReference.ComponentFormat.Column) { newVec2 -> newVec2 }
                        return getValueReferenceEvaluation(valueReference, "Position", includeInterpretation)
                    }
                }
            },
            RotationArgument::class.java to NodeEvaluator { argumentName, context, mapper, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val vec2 = RotationArgument.getRotation(context, argumentName).getRotation(context.source)
                        val valueReference = Vec2fValueReference(mapper, vec2, Vec2fValueReference.ComponentFormat.Rotation) { newVec2 -> newVec2 }
                        return getValueReferenceEvaluation(valueReference, "Rotation", includeInterpretation)
                    }
                }
            },
            Vec2Argument::class.java to NodeEvaluator { argumentName, context, mapper, includeInterpretation ->
                object : EvaluationProvider {
                    override fun evaluate(args: EvaluateArguments): CompletableFuture<EvaluationProvider.EvaluationResult?> {
                        val vec2 = Vec2Argument.getVec2(context, argumentName)
                        val valueReference = Vec2fValueReference(mapper, vec2, Vec2fValueReference.ComponentFormat.Normal) { newVec2 -> newVec2 }
                        return getValueReferenceEvaluation(valueReference, "Rotation", includeInterpretation)
                    }
                }
            },
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
}