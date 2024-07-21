package net.papierkorb2292.command_crafter.editor.debugger.helper

import com.mojang.brigadier.context.StringRange
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.PackagedId

interface FinalTagRangeEntriesProvider {
    fun `command_crafter$getFinalRangeTags`(): Map<PackagedId, Pair<List<String>, Map<StringRange, Map<Identifier, Int>>>>?
}