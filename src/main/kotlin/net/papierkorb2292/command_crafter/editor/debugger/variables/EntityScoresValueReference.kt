package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.entity.Entity
import net.minecraft.world.scores.Objective
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class EntityScoresValueReference(
    private val mapper: VariablesReferenceMapper,
    private val entity: Entity,
    private val source: CommandSourceStack,
    private val filterEmptyObjectives: Boolean,
    private val onUpdate: (Objective) -> Unit
) : VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {
    companion object {
        const val TYPE = "Entity-Score-Map"
        const val EMPTY_OBJECTIVES_FIELD = "[Empty Objectives]" // Not an objective name cause of whitespace
    }

    private val valueReferences = mutableMapOf<String, VariableValueReference>()
    private var objectiveCount = 0

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences.clear()
        objectiveCount = 0
        val scoreboard = source.server.scoreboard
        for(objective in scoreboard.objectives) {
            if(filterEmptyObjectives && scoreboard.getPlayerScoreInfo(entity, objective) != null)
                continue
            objectiveCount++
            if(!filterEmptyObjectives && scoreboard.getPlayerScoreInfo(entity, objective) == null) {
                continue
            }
            valueReferences[objective.name] = ScoreHolderValueReference(mapper, entity, objective, source, includeName = false, allowEntityChild = false) {
                updateValueReferences()
                onUpdate(objective)
            }
        }
        if(!filterEmptyObjectives) {
            valueReferences[EMPTY_OBJECTIVES_FIELD] = EntityScoresValueReference(
                mapper,
                entity,
                source,
                filterEmptyObjectives = true
            ) {
                updateValueReferences()
                onUpdate(it)
            }
        }
    }

    override val namedVariableCount: Int
        get() = valueReferences.size
    override val indexedVariableCount: Int
        get() = 0

    private var variablesReferencerId: Int? = null

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, null, valueReferences)
            .thenApply { variables ->
                // Make the empty objectives field appear greyed out to distinguish it from the scores
                variables.firstOrNull { it.name == EMPTY_OBJECTIVES_FIELD }?.presentationHint = VariablePresentationHint().apply {
                    visibility = VariablePresentationHintVisibility.INTERNAL
                }
                variables
            }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> =
        VariablesReferencer.setVariablesFromCollection(args, null, valueReferences)


    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = if(objectiveCount == 1) "Entity Scores [1 objective]" else "Entity Scores [${objectiveCount} objectives]"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun setValue(value: String) { }
}