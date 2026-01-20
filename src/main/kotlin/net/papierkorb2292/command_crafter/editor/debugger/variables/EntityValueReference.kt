package net.papierkorb2292.command_crafter.editor.debugger.variables

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.advancements.criterion.NbtPredicate
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.storage.TagValueInput
import org.eclipse.lsp4j.debug.*
import java.util.concurrent.CompletableFuture

class EntityValueReference(
    private val mapper: VariablesReferenceMapper,
    private var entity: Entity?,
    private val source: CommandSourceStack,
    private val entitySetter: (Entity?) -> Entity?
): VariableValueReference, CountedVariablesReferencer, IdentifiedVariablesReferencer {

    companion object {
        const val TYPE = "Entity"
        const val NBT_VARIABLE_NAME = "NBT"
        const val SCORES_VARIABLE_NAME = "Scores"
    }

    private val valueReferences = mutableMapOf<String, VariableValueReference>()

    init { updateValueReferences() }
    private fun updateValueReferences() {
        valueReferences.clear()
        val entity = entity ?: return
        valueReferences[NBT_VARIABLE_NAME] = createEntityNbtValueReference(entity)
        valueReferences[SCORES_VARIABLE_NAME] = createEntityScoresValueReference(entity)
    }

    private fun createEntityNbtValueReference(entity: Entity) = NbtValueReference(mapper, NbtPredicate.getEntityTagToCompare(entity)) {
            if (it is CompoundTag)
                entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), it));
            NbtPredicate.getEntityTagToCompare(entity)
        }
    private fun createEntityScoresValueReference(entity: Entity) = EntityScoresValueReference(mapper, entity, source, false) { }

    private var variablesReferencerId: Int? = null

    private fun getValue(): String {
        val entity = entity
        if(entity != null) {
            val customName = entity.customName
            if(customName != null)
                return customName.string
            return "${entity.type.description.string} ${entity.stringUUID}"
        }
        return VariableValueReference.NONE_VALUE
    }

    override fun getEvaluateResponse() = EvaluateResponse().also {
        it.result = getValue()
        it.type = TYPE
        it.variablesReference = getVariablesReferencerId()
        it.namedVariables = namedVariableCount
        it.indexedVariables = indexedVariableCount
    }

    override fun getVariablesReferencerId() = variablesReferencerId ?: mapper.addVariablesReferencer(this).also {
        variablesReferencerId = it
    }

    override fun setValue(value: String) = entitySetter.let {
        if (VariableValueReference.isNone(value)) {
            entity = it(null)
            updateValueReferences()
            return
        }
        try {
            val newEntity = EntitySelectorParser(StringReader(value), true).parse().findEntities(source).firstOrNull()
            entity = it(newEntity)
            updateValueReferences()
        } catch(_: CommandSyntaxException) { }
    }

    override val namedVariableCount: Int
        get() = if(entity != null) 1 else 0
    override val indexedVariableCount: Int
        get() = 0

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> =
        VariablesReferencer.getVariablesFromCollection(args, null, valueReferences)

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> =
        VariablesReferencer.setVariablesFromCollection(args, null, valueReferences)
}