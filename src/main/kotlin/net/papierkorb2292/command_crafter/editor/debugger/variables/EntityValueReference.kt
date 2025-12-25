package net.papierkorb2292.command_crafter.editor.debugger.variables

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.world.entity.Entity
import net.minecraft.nbt.CompoundTag
import net.minecraft.advancements.criterion.NbtPredicate
import net.minecraft.commands.CommandSourceStack
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.util.ProblemReporter
import org.eclipse.lsp4j.debug.SetVariableArguments
import org.eclipse.lsp4j.debug.SetVariableResponse
import org.eclipse.lsp4j.debug.Variable
import org.eclipse.lsp4j.debug.VariablesArguments
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
    }

    private var entityNbtValueReference = createEntityNbtValueReference()

    private fun createEntityNbtValueReference() = entity?.let { entity ->
        NbtValueReference(mapper, NbtPredicate.getEntityTagToCompare(entity)) {
            if (it is CompoundTag)
                entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), it));
            NbtPredicate.getEntityTagToCompare(entity)
        }
    }

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

    override fun setValue(value: String) = entitySetter.let {
        if (VariableValueReference.isNone(value)) {
            entity = it(null)
            entityNbtValueReference = createEntityNbtValueReference()
            return
        }
        try {
            val newEntity = EntitySelectorParser(StringReader(value), true).parse().findEntities(source).firstOrNull()
            entity = it(newEntity)
            entityNbtValueReference = createEntityNbtValueReference()
        } catch(_: CommandSyntaxException) { }
    }

    override val namedVariableCount: Int
        get() = if(entity != null) 1 else 0
    override val indexedVariableCount: Int
        get() = 0

    override fun getVariables(args: VariablesArguments): CompletableFuture<Array<Variable>> {
        val entityNbtValueReferencer = entityNbtValueReference
        if(args.start.run { this == null || this == 0 } && args.count.run { this == null || this > 0 } && entityNbtValueReferencer != null) {
            return CompletableFuture.completedFuture(arrayOf(entityNbtValueReferencer.getVariable(NBT_VARIABLE_NAME)))
        }
        return CompletableFuture.completedFuture(arrayOf())
    }

    override fun setVariable(args: SetVariableArguments): CompletableFuture<VariablesReferencer.SetVariableResult?> {
        val entityNbtValueReferencer = entityNbtValueReference
        if (args.name != NBT_VARIABLE_NAME || entityNbtValueReferencer == null) {
            return CompletableFuture.completedFuture(null)
        }
        entityNbtValueReferencer.setValue(args.value)
        return CompletableFuture.completedFuture(VariablesReferencer.SetVariableResult(entityNbtValueReferencer.getSetVariableResponse()))
    }
}