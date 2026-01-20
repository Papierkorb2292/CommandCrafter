package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.ScoreHolder
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class ScoreHolderMapValueReference(
    private val mapper: VariablesReferenceMapper,
    private val scoreHolders: List<ScoreHolder>,
    private val objective: Objective,
    private val source: CommandSourceStack,
    private val compactEmptyScores: Boolean,
    private val onUpdate: (ScoreHolder) -> Unit
) : VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {
    companion object {
        const val TYPE = "Score-Map"
        const val EMPTY_SCORES_FIELD = "[Empty Scores]" // Not a score name cause of whitespace
    }

    private val valueReferences = mutableMapOf<String, VariableValueReference>()

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences.clear()
        val emptyScores = mutableListOf<ScoreHolder>()
        for(scoreHolder in scoreHolders) {
            if(source.server.scoreboard.getPlayerScoreInfo(scoreHolder, objective) == null && compactEmptyScores) {
                emptyScores.add(scoreHolder)
                continue
            }
            valueReferences[scoreHolder.scoreboardName] = ScoreHolderValueReference(mapper, scoreHolder, objective, source, includeName = false, allowEntityChild = true) {
                updateValueReferences()
                onUpdate(scoreHolder)
            }
        }
        if(compactEmptyScores) {
            valueReferences[EMPTY_SCORES_FIELD] = ScoreHolderMapValueReference(
                mapper,
                emptyScores,
                objective,
                source,
                compactEmptyScores = false
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
                // Make the empty scores field appear greyed out to distinguish it from the scores
                variables.firstOrNull { it.name == EMPTY_SCORES_FIELD }?.presentationHint = VariablePresentationHint().apply {
                    visibility = VariablePresentationHintVisibility.INTERNAL
                }
                variables
            }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> =
        VariablesReferencer.setVariablesFromCollection(args, null, valueReferences)


    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = if(scoreHolders.size == 1) "Score Map [1 score]" else "Score Map [${scoreHolders.size} scores]"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun setValue(value: String) { }
}