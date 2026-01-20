package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.entity.Entity
import org.eclipse.lsp4j.debug.EvaluateResponse
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import java.util.concurrent.CompletableFuture

class EntityListValueReference(
    private val mapper: VariablesReferenceMapper,
    private val entities: List<Entity>,
    private val source: CommandSourceStack,
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {
    companion object {
        const val TYPE = "EntityList"
    }

    private val valueReferences = entities.map {
        entity -> EntityValueReference(mapper, entity, source) { newValue -> entity }
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = if(entities.size == 1) "Entity List [1 entity]" else "Entity List [${entities.size} entities]"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun setValue(value: String) {
        // Not mutable
    }

    override val namedVariableCount: Int
        get() = 0
    override val indexedVariableCount: Int
        get() = entities.size

    private var variablesReferencerId: Int? = null

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, valueReferences, null)

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> =
        VariablesReferencer.setVariablesFromCollection(args, valueReferences, null)
}