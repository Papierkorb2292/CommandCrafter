package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.util.math.Vec3d
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class Vec3dValueReference(
    private val mapper: VariablesReferenceMapper,
    private var vec3d: Vec3d?,
    private val vec3dSetter: (Vec3d?) -> Vec3d?
): VariableValueReference, CountedVariablesReferencer {

    companion object {
        const val TYPE = "Vec3d"
        const val X_COMPONENT_NAME = "x"
        const val Y_COMPONENT_NAME = "y"
        const val Z_COMPONENT_NAME = "z"
    }

    private var variablesReferencerId: Int? = null
    private val valueReferences = mutableMapOf<String, DoubleValueReference>()

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences.clear()
        vec3d?.run {
            valueReferences[X_COMPONENT_NAME] = DoubleValueReference(x) {
                if(it == null) return@DoubleValueReference x
                val newVec = vec3dSetter(Vec3d(it, y, z))
                vec3d = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.x
            }
            valueReferences[Y_COMPONENT_NAME] = DoubleValueReference(y) {
                if(it == null) return@DoubleValueReference y
                val newVec = vec3dSetter(Vec3d(x, it, z))
                vec3d = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.y
            }
            valueReferences[Z_COMPONENT_NAME] = DoubleValueReference(z) {
                if(it == null) return@DoubleValueReference z
                val newVec = vec3dSetter(Vec3d(x, y, it))
                vec3d = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.z
            }
        }
    }

    override fun getVariable(name: String) = Variable().also {
        it.name = name
        val vec3d = vec3d
        it.value = if(vec3d == null) VariableValueReference.NONE_VALUE else "${vec3d.x}, ${vec3d.y}, ${vec3d.z}"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun getSetVariableResponse() = SetVariableResponse().also {
        val vec3d = vec3d
        it.value = if(vec3d == null) VariableValueReference.NONE_VALUE else "${vec3d.x}, ${vec3d.y}, ${vec3d.z}"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    private fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun setValue(value: String) {
        vec3d = vec3dSetter(
            if(VariableValueReference.isNone(value)) null
            else value.split(",").let {
                if(it.size != 3) return@let null
                Vec3d(it[0].toDoubleOrNull() ?: return@let null,
                    it[1].toDoubleOrNull() ?: return@let null,
                    it[2].toDoubleOrNull() ?: return@let null)
            }
        )
        updateValueReferences()
    }

    override val namedVariableCount: Int
        get() = if(vec3d != null) 3 else 0
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