package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.world.phys.Vec2
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class Vec2fValueReference(
    val mapper: VariablesReferenceMapper,
    private var vec2f: Vec2?,
    private val vec2dSetter: (Vec2?) -> Vec2?
): VariableValueReference, CountedVariablesReferencer {

    companion object {
        const val TYPE = "Vec2d"
        const val X_COMPONENT_NAME = "x"
        const val Y_COMPONENT_NAME = "y"
    }

    private var variablesReferencerId: Int? = null
    private val valueReferences = mutableMapOf<String, DoubleValueReference>()

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences.clear()
        vec2f?.run {
            valueReferences[X_COMPONENT_NAME] = DoubleValueReference(x.toDouble()) {
                if(it == null) return@DoubleValueReference x.toDouble()
                val newVec = vec2dSetter(Vec2(it.toFloat(), y))
                vec2f = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.x.toDouble()
            }
            valueReferences[Y_COMPONENT_NAME] = DoubleValueReference(y.toDouble()) {
                if(it == null) return@DoubleValueReference y.toDouble()
                val newVec = vec2dSetter(Vec2(x, it.toFloat()))
                vec2f = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.y.toDouble()
            }
        }
    }

    override fun getVariable(name: String) = Variable().also {
        it.name = name
        val vec3d = vec2f
        it.value = if(vec3d == null) VariableValueReference.NONE_VALUE else "${vec3d.x}, ${vec3d.y}"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        val vec3d = vec2f
        it.value = if(vec3d == null) VariableValueReference.NONE_VALUE else "${vec3d.x}, ${vec3d.y}"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    private fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun setValue(value: String) {
        vec2f = vec2dSetter(
            if(VariableValueReference.isNone(value)) null
            else value.split(",").let {
                if(it.size != 2) return@let null
                Vec2(it[0].toFloatOrNull() ?: return@let null,
                    it[1].toFloatOrNull() ?: return@let null)
            }
        )
        updateValueReferences()
    }

    override val namedVariableCount: Int
    get() = if(vec2f != null) 2 else 0
    override val indexedVariableCount: Int
    get() = 0

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        if(args.filter == VariablesArgumentsFilter.INDEXED) return CompletableFuture.completedFuture(emptyArray())
        val start = args.start ?: 0
        val count = args.count ?: (valueReferences.size - start)
        return CompletableFuture.completedFuture(valueReferences.entries.drop(start).take(count).map {
                (name, value) -> value.getVariable(name)
        }.toTypedArray())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val valueReference = valueReferences[args.name]
            ?: return CompletableFuture.completedFuture(null)
        valueReference.setValue(args.value)
        return CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(valueReference.getSetVariableResponse(), true))
    }
}
