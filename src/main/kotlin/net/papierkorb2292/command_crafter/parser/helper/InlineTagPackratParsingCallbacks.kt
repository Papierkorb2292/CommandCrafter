package net.papierkorb2292.command_crafter.parser.helper
import net.minecraft.core.HolderGetter
import net.minecraft.core.HolderSet
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey

interface InlineTagPackratParsingCallbacks<TPredicate: Any, TagEntry: Any> {
    fun `command_crafter$getRegistry`(): HolderGetter<TagEntry>
    fun `command_crafter$getRegistryKey`(): ResourceKey<out Registry<TagEntry>>
    fun `command_crafter$getPredicate`(tag: HolderSet<TagEntry>): TPredicate
}