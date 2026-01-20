package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.entity.Entity
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.ScoreHolder
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class ScoreHolderValueReference(
    val mapper: VariablesReferenceMapper,
    val scoreHolder: ScoreHolder,
    val objective: Objective,
    val source: CommandSourceStack,
    val includeName: Boolean,
    allowEntityChild: Boolean,
    val onUpdate: () -> Unit
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {

    private val valueDelegate = IntValueReference(
        source.server.scoreboard.getPlayerScoreInfo(scoreHolder, objective)?.value()
    ) { newValue ->
        if(newValue != null) {
            val mutable = source.server.scoreboard.getOrCreatePlayerScore(scoreHolder, objective)
            mutable.set(newValue)
        } else {
            source.server.scoreboard.resetSinglePlayerScore(scoreHolder, objective)
        }
        onUpdate()
        newValue
    }

    private val valueReferences =
        if(allowEntityChild && scoreHolder is Entity) mapOf("Entity" to EntityValueReference(mapper, scoreHolder, source) { newValue -> scoreHolder })
        else emptyMap()

    private var variablesReferencerId: Int? = null

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun getEvaluateResponse(): EvaluateResponse {
        val value = valueDelegate.getEvaluateResponse()
        if(valueReferences.isNotEmpty()) {
            value.variablesReference = getVariablesReferencerId()
            value.namedVariables = namedVariableCount
            value.indexedVariables = indexedVariableCount
        }
        if(includeName) {
            value.result = "${scoreHolder.scoreboardName}: ${value.result}"
        }
        return value
    }

    override fun setValue(value: String) = valueDelegate.setValue(value)

    override val namedVariableCount: Int
        get() = valueReferences.size
    override val indexedVariableCount: Int
        get() = 0

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, null, valueReferences)

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> =
        VariablesReferencer.setVariablesFromCollection(args, null, valueReferences)
}