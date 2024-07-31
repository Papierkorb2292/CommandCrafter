package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import net.papierkorb2292.command_crafter.editor.debugger.variables.*
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class CommandResultValueReference(
    private val mapper: VariablesReferenceMapper,
    private var commandResult: CommandResult,
    private val commandResultSetter: (CommandResult) -> CommandResult
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {

    companion object {
        const val TYPE = "CommandResult"
        const val SUCCESSFUL_VARIABLE_NAME = "success"
        const val RESULT_VARIABLE_NAME = "result"
    }

    override val namedVariableCount: Int
        get() = contentValueReferences.size
    override val indexedVariableCount: Int
        get() = 0

    private var contentValueReferences = mutableListOf<Pair<String, VariableValueReference>>()

    init { updateVariableValueReferences() }
    private fun updateVariableValueReferences() {
        contentValueReferences.clear()
        val returnValue = commandResult.returnValue ?: return
        val (success, result) = returnValue
        contentValueReferences += SUCCESSFUL_VARIABLE_NAME to BooleanValueReference(success) {
            if(it == null) return@BooleanValueReference success
            updateCommandResult(commandResultSetter(CommandResult(Pair(it, result))))
            commandResult.returnValue?.first ?: true  // If 'returnValue' is null, this ValueReference instance won't be used anymore anyway
        }
        contentValueReferences += RESULT_VARIABLE_NAME to IntValueReference(result) {
            if(it == null) return@IntValueReference result
            updateCommandResult(commandResultSetter(CommandResult(Pair(success, it))))
            commandResult.returnValue?.second ?: 0  // If 'returnValue' is null, this ValueReference instance won't be used anymore anyway
        }
    }

    private fun updateCommandResult(newCommandResult: CommandResult) {
        val setterCommandResult = commandResultSetter(newCommandResult)
        if(setterCommandResult == commandResult) return
        commandResult = setterCommandResult
        updateVariableValueReferences()
    }

    private var variablesReferencerId: Int? = null

    private fun getValue(): String {
        return commandResult.returnValue?.let { (success, result) ->
            "$success; $result"
        } ?: VariableValueReference.NONE_VALUE
    }

    override fun getVariable(name: String) = Variable().also {
        it.name = name
        it.value = getValue()
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        it.value = getValue()
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun setValue(value: String) {
        if(VariableValueReference.isNone(value)) {
            updateCommandResult(commandResultSetter(CommandResult(null)))
            return
        }
        val semicolonIndex = value.indexOf(';')
        val success: Boolean?
        val result: Int?
        if(semicolonIndex == -1) {
            result = value.toIntOrNull()
            success = if(result == null) value.toBooleanStrictOrNull() else null
        } else {
            success = value.substring(0, semicolonIndex).trim().toBooleanStrictOrNull()
            result = value.substring(semicolonIndex + 1).trim().toIntOrNull()
        }
        updateCommandResult(commandResultSetter(CommandResult(
            if(success == null) {
                if(result == null) null
                else Pair(commandResult.returnValue?.first ?: true, result)
            } else {
                if(result == null) Pair(success, commandResult.returnValue?.second ?: 0)
                else Pair(success, result)
            }
        )))
    }

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        if(args.filter == VariablesArgumentsFilter.INDEXED) return CompletableFuture.completedFuture(emptyArray())
        val start = args.start ?: 0
        val end = args.count?.let { it + start } ?: contentValueReferences.size
        return CompletableFuture.completedFuture(contentValueReferences.subList(start, end).map {
            (name, valueReference) -> valueReference.getVariable(name)
        }.toTypedArray())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val valueReference = contentValueReferences.find { it.first == args.name }?.second
            ?: return CompletableFuture.completedFuture(null)
        valueReference.setValue(args.value)
        return CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(valueReference.getSetVariableResponse(), true))
    }
}