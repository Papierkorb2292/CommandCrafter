package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import net.minecraft.registry.tag.TagEntry
import net.minecraft.registry.tag.TagGroupLoader
import net.minecraft.util.Identifier

class TagFinalEntriesValueGetter(
    private val tagId: Identifier,
    private val currentEntry: TagGroupLoader.TrackedEntry,
    private val parsedTags: Map<Identifier, List<TagGroupLoader.TrackedEntry>>,
    private val finalEntries: MutableMap<Identifier, Collection<FinalEntry>> = mutableMapOf()
) : TagEntry.ValueGetter<TagFinalEntriesValueGetter.FinalEntry> {
    override fun direct(id: Identifier): FinalEntry = FinalEntry(tagId, currentEntry, null)

    override fun tag(id: Identifier): Collection<FinalEntry> {
        finalEntries[id]?.let { return it }
        val result = mutableListOf<FinalEntry>()
        parsedTags[id]!!.forEach { trackedEntry ->
            trackedEntry.entry.resolve(TagFinalEntriesValueGetter(id, trackedEntry, parsedTags, finalEntries)) { finalEntry ->
                result += FinalEntry(id, currentEntry, finalEntry)
            }
        }
        finalEntries[id] = result
        return result
    }

    data class FinalEntry(
        val sourceId: Identifier,
        val trackedEntry: TagGroupLoader.TrackedEntry,
        val child: FinalEntry?
    ) {
        fun getLastChild(): FinalEntry = child?.getLastChild() ?: this
    }
}