package net.papierkorb2292.command_crafter.editor.debugger.variables

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.arguments.item.ItemInput
import net.minecraft.commands.arguments.item.ItemParser
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.TypedDataComponent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.world.entity.SlotProvider
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.eclipse.lsp4j.debug.EvaluateResponse

class SlotAccessValueReference(
    val mapper: VariablesReferenceMapper,
    val slotProvider: SlotProvider,
    val slot: Int,
    val includeName: Boolean,
    val registries: HolderLookup.Provider,
): VariableValueReference {

    companion object {
        const val TYPE = "Slot"

        private fun serializeItem(stack: ItemStack, registries: HolderLookup.Provider): Tag {
            return ItemStack.OPTIONAL_CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries), stack).result().get()
        }

        private fun deserializeItem(tag: Tag, registries: HolderLookup.Provider): ItemStack {
            return ItemStack.OPTIONAL_CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), tag).result().get()
        }

        fun formatItem(item: Holder<Item>, components: DataComponentPatch.SplitResult, count: Int, registries: HolderLookup.Provider): String {
            val stringBuilder = StringBuilder()

            for(added in components.added()) {
                val encoded: Tag = formatComponent(added, registries) ?: continue
                if(!stringBuilder.isEmpty()) stringBuilder.append(',')
                stringBuilder.append(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(added.type())!!.toShortString())
                stringBuilder.append('=')
                stringBuilder.append(encoded)
            }
            for(removed in components.removed()) {
                if(!stringBuilder.isEmpty()) stringBuilder.append(',')
                stringBuilder.append('!')
                stringBuilder.append(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(removed)!!.toShortString())
            }

            if(!stringBuilder.isEmpty()) {
                stringBuilder.insert(0, '[')
                stringBuilder.append(']')
            }

            stringBuilder.insert(0, item.registeredName)

            if(count > 1) {
                stringBuilder.append(' ')
                stringBuilder.append(count)
            }

            return stringBuilder.toString()
        }

        fun <T: Any> formatComponent(component: TypedDataComponent<T>, registries: HolderLookup.Provider): Tag? {
            val codec = component.type().codec() ?: return null;
            val encoded = codec.encode(
                component.value(),
                RegistryOps.create(NbtOps.INSTANCE, registries),
                NbtOps.INSTANCE.empty()
            );
            return encoded.result().orElse(CompoundTag());
        }
    }

    private val slotAccess = slotProvider.getSlot(slot)
    private var valueDelegate: VariableValueReference? = null

    init { updateValueReferences() }
    private fun updateValueReferences() {
        val slotAccess = slotAccess
        valueDelegate =
            if(slotAccess == null) null
            else NbtValueReference(mapper, serializeItem(slotAccess.get(), registries)) { newValue ->
                val itemStack = if(newValue != null) deserializeItem(newValue, registries) else ItemStack.EMPTY
                slotAccess.set(itemStack)
                serializeItem(slotAccess.get(), registries)
            }
    }

    override fun getEvaluateResponse(): EvaluateResponse {
        val valueDelegate = valueDelegate ?: return EvaluateResponse().also {
            it.result = VariableValueReference.NONE_VALUE
            it.type = TYPE
        }
        val stack = slotAccess!!.get()
        val value = valueDelegate.getEvaluateResponse()
        value.type = TYPE
        value.result = formatItem(stack.typeHolder(), stack.componentsPatch.split(), stack.count, registries)
        if(includeName) {
            value.result = "${SlotProviderMapValueReference.getNameForProvider(slotProvider)}: ${value.result}"
        }
        return value
    }

    override fun setValue(value: String) {
        val slotAccess = slotAccess ?: return
        try {
            val reader = StringReader(value)
            val parsed = ItemParser(registries).parse(reader)
            val count = reader.readInt()
            val stack = ItemInput(parsed.item, parsed.components)
                .createItemStack(count)
            slotAccess.set(stack)
            updateValueReferences()
        } catch(_: CommandSyntaxException) { }
    }
}