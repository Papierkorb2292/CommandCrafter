package net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags

import net.minecraft.registry.tag.TagEntry
import net.minecraft.registry.tag.TagGroupLoader
import net.minecraft.util.Identifier

class TagFinalEntriesValueGetter(
    private val tagId: Identifier,
    private val currentEntry: TagGroupLoader.TrackedEntry,
    private val parsedTags: Map<Identifier, List<TagGroupLoader.TrackedEntry>>,
    private val tagCache: MutableMap<Identifier, MutableCollection<Pair<Identifier, TagGroupLoader.TrackedEntry>>> = mutableMapOf()
) : TagEntry.ValueGetter<Pair<Identifier, TagGroupLoader.TrackedEntry>> {
    override fun direct(id: Identifier): Pair<Identifier, TagGroupLoader.TrackedEntry> = tagId to currentEntry

    override fun tag(id: Identifier): MutableCollection<Pair<Identifier, TagGroupLoader.TrackedEntry>> {
        tagCache[id]?.let { return it }
        val result = mutableListOf<Pair<Identifier, TagGroupLoader.TrackedEntry>>()
        parsedTags[id]!!.forEach {
            it.entry.resolve(TagFinalEntriesValueGetter(id, it, parsedTags, tagCache), result::add)
        }
        tagCache[id] = result
        return result
    }
}