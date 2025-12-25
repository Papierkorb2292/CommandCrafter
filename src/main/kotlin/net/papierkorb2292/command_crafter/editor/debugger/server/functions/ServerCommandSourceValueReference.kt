package net.papierkorb2292.command_crafter.editor.debugger.server.functions

import net.minecraft.commands.CommandSourceStack
import net.papierkorb2292.command_crafter.editor.debugger.variables.*
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class ServerCommandSourceValueReference(
    private val mapper: VariablesReferenceMapper,
    private var source: CommandSourceStack,
    private val setter: ((CommandSourceStack) -> Unit)? = null
) : VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {
    companion object {
        const val ENTITY_VARIABLE_NAME = "@s"
        const val DIMENSION_VARIABLE_NAME = "dimension"
        const val POS_VARIABLE_NAME = "pos"
        const val ROTATION_VARIABLE_NAME = "rotation"
    }

    private var variablesReferencerId: Int? = null

    private val content = HashMap<String, VariableValueReference>()

    init { updateVariableValueReferences() }
    private fun updateVariableValueReferences() {
        content.clear()
        content[ENTITY_VARIABLE_NAME] = EntityValueReference(mapper, source.entity, this.source) { newEntity ->
            this.setter?.let {
                updateSource(
                    if (newEntity == null) {
                        this.source
                    } else this.source.withEntity(newEntity),
                    it
                )
            }
            source.entity
        }
        content[DIMENSION_VARIABLE_NAME] = IdValueReference(source.level.dimension().identifier()) { newDimensionId ->
            this.setter?.let { setter ->
                val newRegistryKey = source.levels().firstOrNull { it.identifier() == newDimensionId }
                if(newRegistryKey != null) {
                    updateSource(
                        this.source.withLevel(source.server.getLevel(newRegistryKey)!!),
                        setter
                    )
                }
            }
            return@IdValueReference source.level.dimension().identifier()
        }
        content[POS_VARIABLE_NAME] = Vec3dValueReference(mapper, source.position) { newPosition ->
            this.setter?.let { setter ->
                if(newPosition != null) {
                    updateSource(
                        this.source.withPosition(newPosition),
                        setter
                    )
                }
            }
            return@Vec3dValueReference source.position
        }
        content[ROTATION_VARIABLE_NAME] = Vec2fValueReference(mapper, source.rotation) { newRotation ->
            this.setter?.let { setter ->
                if(newRotation != null) {
                    updateSource(
                        this.source.withRotation(newRotation),
                        setter
                    )
                }
            }
            source.rotation
        }
    }

    private fun updateSource(source: CommandSourceStack, setter: (CommandSourceStack) -> Unit) {
        if(this.source != source) {
            this.source = source
            setter.invoke(source)
            updateVariableValueReferences()
        }
    }

    override fun getVariable(name: String) = Variable().also {
        it.name = name
        it.value = source.textName
        it.type = "ServerCommandSource"
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = content.size
        it.indexedVariables = 0
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        it.value = source.textName
        it.type = "ServerCommandSource"
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = content.size
        it.indexedVariables = 0
    }

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun setValue(value: String) { }
    override val namedVariableCount: Int
        get() = content.size
    override val indexedVariableCount: Int
        get() = 0

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        if(args.filter == VariablesArgumentsFilter.INDEXED) return CompletableFuture.completedFuture(emptyArray())
        val start = args.start ?: 0
        val count = args.count ?: (content.size - start)
        return CompletableFuture.completedFuture(content.entries.drop(start).take(count).map {
            (name, value) -> value.getVariable(name)
        }.toTypedArray())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val valueReference = content[args.name]
            ?: return CompletableFuture.completedFuture(null)
        valueReference.setValue(args.value)
        return CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(valueReference.getSetVariableResponse(), true))
    }
}