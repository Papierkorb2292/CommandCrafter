package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import com.google.common.collect.ImmutableSet
import net.minecraft.tags.TagEntry
import net.minecraft.tags.TagLoader
import net.minecraft.resources.Identifier

class TagFinalEntriesValueGetter(
    private val tagId: Identifier,
    private val currentEntry: TagLoader.EntryWithSource,
    private val parsedTags: Map<Identifier, List<TagLoader.EntryWithSource>>,
    private val finalEntries: MutableMap<Identifier, Collection<FinalEntry>> = mutableMapOf()
) : TagEntry.Lookup<TagFinalEntriesValueGetter.FinalEntry> {
    companion object {
        fun getOrCreateFinalEntriesForTag(id: Identifier, parsedTags: Map<Identifier, List<TagLoader.EntryWithSource>>, finalEntries: MutableMap<Identifier, Collection<FinalEntry>>): Collection<FinalEntry> {
            finalEntries[id]?.let { return it }
            val resultBuilder = ImmutableSet.builder<FinalEntry>()
            parsedTags[id]!!.forEach { trackedEntry ->
                trackedEntry.entry.build(TagFinalEntriesValueGetter(id, trackedEntry, parsedTags, finalEntries), resultBuilder::add)
            }
            val result = resultBuilder.build()
            finalEntries[id] = result
            return result
        }
    }

    override fun element(id: Identifier, required: Boolean): FinalEntry = FinalEntry(tagId, currentEntry, null)

    override fun tag(id: Identifier): Collection<FinalEntry> {
        return getOrCreateFinalEntriesForTag(id, parsedTags, finalEntries).map { FinalEntry(tagId, currentEntry, it) }
    }

    data class FinalEntry(
        val sourceId: Identifier,
        val trackedEntry: TagLoader.EntryWithSource,
        val child: FinalEntry?
    ) {
        fun getLastChild(): FinalEntry = child?.getLastChild() ?: this
    }
}