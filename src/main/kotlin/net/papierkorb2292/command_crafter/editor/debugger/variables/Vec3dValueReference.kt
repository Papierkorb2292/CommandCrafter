package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.world.phys.Vec3
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class Vec3dValueReference(
    private val mapper: VariablesReferenceMapper,
    private var vec3d: Vec3?,
    private val vec3dSetter: (Vec3?) -> Vec3?
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
                val newVec = vec3dSetter(Vec3(it, y, z))
                vec3d = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.x
            }
            valueReferences[Y_COMPONENT_NAME] = DoubleValueReference(y) {
                if(it == null) return@DoubleValueReference y
                val newVec = vec3dSetter(Vec3(x, it, z))
                vec3d = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.y
            }
            valueReferences[Z_COMPONENT_NAME] = DoubleValueReference(z) {
                if(it == null) return@DoubleValueReference z
                val newVec = vec3dSetter(Vec3(x, y, it))
                vec3d = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.z
            }
        }
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        val vec3d = vec3d
        it.result = if(vec3d == null) VariableValueReference.NONE_VALUE else "${X_COMPONENT_NAME}=${vec3d.x}, ${Y_COMPONENT_NAME}=${vec3d.y}, ${Z_COMPONENT_NAME}=${vec3d.z}"
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    private fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    private fun indexFromComponent(component: String): Int = when(component) {
        X_COMPONENT_NAME -> 0; Y_COMPONENT_NAME -> 1; Z_COMPONENT_NAME -> 2
        else -> -1
    }

    override fun setValue(value: String) {
        vec3d = vec3dSetter(
            if(VariableValueReference.isNone(value)) null
            else value.split(",").let { entries ->
                val newValues = doubleArrayOf(vec3d?.x ?: 0.0, vec3d?.y ?: 0.0, vec3d?.z ?: 0.0)
                val remainingComponents = mutableSetOf(X_COMPONENT_NAME, Y_COMPONENT_NAME, Z_COMPONENT_NAME)
                for(entry in entries) {
                    val keyValue = entry.split("=")
                    if(keyValue.isEmpty()) continue
                    var key = keyValue.first().trim()
                    var index = indexFromComponent(key)
                    if(index == -1) {
                        key = remainingComponents.firstOrNull() ?: continue
                        index = indexFromComponent(key)
                    }
                    remainingComponents -= key
                    val value = keyValue.asReversed().firstNotNullOfOrNull { it.toDoubleOrNull() } ?: continue
                    newValues[index] = value
                }
                Vec3(newValues[0], newValues[1], newValues[2])
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