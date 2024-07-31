package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import com.google.common.collect.ImmutableSet
import net.minecraft.registry.tag.TagEntry
import net.minecraft.registry.tag.TagGroupLoader
import net.minecraft.util.Identifier

class TagFinalEntriesValueGetter(
    private val tagId: Identifier,
    private val currentEntry: TagGroupLoader.TrackedEntry,
    private val parsedTags: Map<Identifier, List<TagGroupLoader.TrackedEntry>>,
    private val finalEntries: MutableMap<Identifier, Collection<FinalEntry>> = mutableMapOf()
) : TagEntry.ValueGetter<TagFinalEntriesValueGetter.FinalEntry> {
    companion object {
        fun getOrCreateFinalEntriesForTag(id: Identifier, parsedTags: Map<Identifier, List<TagGroupLoader.TrackedEntry>>, finalEntries: MutableMap<Identifier, Collection<FinalEntry>>): Collection<FinalEntry> {
            finalEntries[id]?.let { return it }
            val resultBuilder = ImmutableSet.builder<FinalEntry>()
            parsedTags[id]!!.forEach { trackedEntry ->
                trackedEntry.entry.resolve(TagFinalEntriesValueGetter(id, trackedEntry, parsedTags, finalEntries), resultBuilder::add)
            }
            val result = resultBuilder.build()
            finalEntries[id] = result
            return result
        }
    }

    override fun direct(id: Identifier): FinalEntry = FinalEntry(tagId, currentEntry, null)

    override fun tag(id: Identifier): Collection<FinalEntry> {
        return getOrCreateFinalEntriesForTag(id, parsedTags, finalEntries).map { FinalEntry(tagId, currentEntry, it) }
    }

    data class FinalEntry(
        val sourceId: Identifier,
        val trackedEntry: TagGroupLoader.TrackedEntry,
        val child: FinalEntry?
    ) {
        fun getLastChild(): FinalEntry = child?.getLastChild() ?: this
    }
}