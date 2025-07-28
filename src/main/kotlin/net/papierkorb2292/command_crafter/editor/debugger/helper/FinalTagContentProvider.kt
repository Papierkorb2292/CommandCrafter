package net.papierkorb2292.command_crafter.editor.debugger.helper

import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.TagFinalEntriesValueGetter
import net.papierkorb2292.command_crafter.parser.FileMappingInfo

interface FinalTagContentProvider {
    fun `command_crafter$getFileContent`(): Map<PackagedId, FileMappingInfo>
    fun `command_crafter$getFinalTags`(): Map<Identifier, Collection<TagFinalEntriesValueGetter.FinalEntry>>
}