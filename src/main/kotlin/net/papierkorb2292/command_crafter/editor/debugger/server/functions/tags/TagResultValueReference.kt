package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResultValueReference
import net.papierkorb2292.command_crafter.editor.debugger.variables.CountedVariablesReferencer
import net.papierkorb2292.command_crafter.editor.debugger.variables.IdentifiedVariablesReferencer
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferenceMapper
import net.papierkorb2292.command_crafter.editor.debugger.variables.VariablesReferencer
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter
import java.util.concurrent.CompletableFuture

class TagResultValueReference(
    private val mapper: VariablesReferenceMapper,
    private val lastFunctionResult: CommandResultValueReference,
    private val accumulatedResult: CommandResultValueReference
) : CountedVariablesReferencer, IdentifiedVariablesReferencer {
    companion object {
        private const val lastFunctionResultName = "last-function-result"
        private const val accumulatedResultName = "accumulated-result"
    }

    override val namedVariableCount: Int
        get() = 2
    override val indexedVariableCount: Int
        get() = 0

    private var variableReferencerId: Int? = null

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        if(args.filter == VariablesArgumentsFilter.INDEXED) return CompletableFuture.completedFuture(emptyArray())
        val start = args.start ?: 0
        val count = args.count ?: (2 - start)
        if(count <= 0) return CompletableFuture.completedFuture(emptyArray())
        val result = mutableListOf<Variable>()
        if(start == 0)
            result.add(lastFunctionResult.getVariable(lastFunctionResultName))
        if(start == 1 || count > 1)
            result.add(accumulatedResult.getVariable(accumulatedResultName))
        return CompletableFuture.completedFuture(result.toTypedArray())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        return when(args.name) {
            lastFunctionResultName -> {
                lastFunctionResult.setValue(args.value)
                CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(
                    lastFunctionResult.getSetVariableResponse(),
                    false
                ))
            }
            accumulatedResultName -> {
                accumulatedResult.setValue(args.value)
                CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(
                    accumulatedResult.getSetVariableResponse(),
                    false
                ))
            }
            else -> CompletableFuture.completedFuture(null)
        }
    }

    override fun getVariablesReferencerId() = variableReferencerId ?:
    mapper.addVariablesReferencer(this).also { variableReferencerId = it }
}