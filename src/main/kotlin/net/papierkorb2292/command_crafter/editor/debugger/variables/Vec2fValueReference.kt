package net.papierkorb2292.command_crafter.editor.debugger.variables

import net.minecraft.world.phys.Vec2
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class Vec2fValueReference(
    val mapper: VariablesReferenceMapper,
    private var vec2f: Vec2?,
    private val componentFormat: ComponentFormat,
    private val vec2fSetter: (Vec2?) -> Vec2?,
): VariableValueReference, CountedVariablesReferencer {

    companion object {
        const val TYPE = "Vec2f"
    }

    private var variablesReferencerId: Int? = null
    private val valueReferences = mutableMapOf<String, DoubleValueReference>()

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences.clear()
        vec2f?.run {
            valueReferences[componentFormat.first] = DoubleValueReference(x.toDouble()) {
                if(it == null) return@DoubleValueReference x.toDouble()
                val newVec = vec2fSetter(Vec2(it.toFloat(), y))
                vec2f = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.x.toDouble()
            }
            valueReferences[componentFormat.second] = DoubleValueReference(y.toDouble()) {
                if(it == null) return@DoubleValueReference y.toDouble()
                val newVec = vec2fSetter(Vec2(x, it.toFloat()))
                vec2f = newVec
                if(newVec == null) {
                    valueReferences.clear()
                    null
                }
                else newVec.y.toDouble()
            }
        }
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        val vec2f = vec2f
        it.result =
            if(vec2f == null) VariableValueReference.NONE_VALUE
            else componentFormat.format(vec2f)
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    private fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    private fun indexFromComponent(component: String): Int = when(component) {
        componentFormat.first -> 0; componentFormat.second -> 1;
        else -> -1
    }

    override fun setValue(value: String) {
        vec2f = vec2fSetter(
            if(VariableValueReference.isNone(value)) null
            else value.split(",").let { entries ->
                val newValues = floatArrayOf(vec2f?.x ?: 0.0f, vec2f?.y ?: 0.0f)
                val remainingComponents =
                    if(componentFormat.swapOrder) mutableSetOf(componentFormat.second, componentFormat.first)
                    else mutableSetOf(componentFormat.first, componentFormat.second)
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
                    val value = keyValue.asReversed().firstNotNullOfOrNull { it.toFloatOrNull() } ?: continue
                    newValues[index] = value
                }
                Vec2(newValues[0], newValues[1])
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
        val entries = if(componentFormat.swapOrder) valueReferences.entries.reversed() else valueReferences.entries
        return CompletableFuture.completedFuture(entries.drop(start).take(count).map {
                (name, value) -> value.getVariable(name)
        }.toTypedArray())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val valueReference = valueReferences[args.name]
            ?: return CompletableFuture.completedFuture(null)
        valueReference.setValue(args.value)
        return CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(valueReference.getSetVariableResponse(), true))
    }

    enum class ComponentFormat(val first: String, val second: String, val swapOrder: Boolean) {
        Normal("x", "y", false),
        Rotation("x", "y", true),
        Column("x", "z", false);

        fun format(vec: Vec2): String =
            if(swapOrder)
                "${second}=${vec.y}, ${first}=${vec.x}"
            else
                "${first}=${vec.x}, ${second}=${vec.y}"
    }
}
