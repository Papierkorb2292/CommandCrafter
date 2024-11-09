package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.StringRange
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtEnd
import net.minecraft.nbt.NbtOps

class NbtStringRangeListBuilder(private val stringRangeTreeBuilder: StringRangeTree.Builder<NbtElement>?, private val resultConsumer: ((NbtElement) -> Unit)?) {
    companion object {
        fun forStringRangeTreeBuilderAtCurrentNodeOrder(stringRangeTreeBuilder: StringRangeTree.Builder<NbtElement>): NbtStringRangeListBuilder {
            return NbtStringRangeListBuilder(stringRangeTreeBuilder, stringRangeTreeBuilder.registerNodeOrderPlaceholder())
        }

        fun forNoStringRangeTree(): NbtStringRangeListBuilder {
            return NbtStringRangeListBuilder(null, null)
        }
    }

    private var entries = mutableListOf<NbtElement>()

    private val rangesBetweenEntries = mutableListOf<StringRange>()

    fun addElement(element: NbtElement) {
        entries += element
    }

    fun addRangeBetweenEntries(range: StringRange) {
        rangesBetweenEntries.add(range)
    }

    fun build(listStringRange: StringRange, allowedStartCursor: Int? = null): NbtElement {
        val list = if(entries.any { it is NbtEnd }) {
            NbtOps.CompoundListMerger().merge(entries).result
        } else {
            NbtOps.INSTANCE.createList(entries.stream())
        }
        resultConsumer?.invoke(list)
        if(stringRangeTreeBuilder != null) {
            stringRangeTreeBuilder.addNode(list, listStringRange, allowedStartCursor)
            for(range in rangesBetweenEntries)
                stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(list, range)
        }
        return list
    }
}