package net.papierkorb2292.command_crafter.client

import net.minecraft.core.HolderLookup
import net.minecraft.core.HolderOwner
import net.minecraft.core.HolderSet
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import java.util.*
import java.util.stream.Stream

/**
 * A [`HolderLookup.RegistryLookup`] wrapper that delegates all lookups to another registry lookup,
 * but provides a fallback for missing tags, such that there are no failed lookups. This is meant for tags in builtin registries
 * that CommandCrafter syncs. It does not work for dynamic registries, because their tags are only bound after the elements have
 * been decoded.
 */
class DummyMissingTagLookup<T : Any>(
    private val delegate: HolderLookup.RegistryLookup<T>
) : HolderLookup.RegistryLookup<T> {

    override fun key() = delegate.key()
    override fun registryLifecycle() = delegate.registryLifecycle()
    override fun get(resourceKey: ResourceKey<T>) = delegate.get(resourceKey)
    override fun listElements() = delegate.listElements()

    override fun get(tagKey: TagKey<T>): Optional<HolderSet.Named<T>> =
        Optional.of(delegate.get(tagKey).orElse(HolderSet.emptyNamed(this, tagKey))!!)

    override fun listTags(): Stream<HolderSet.Named<T>> = delegate.listTags()

    override fun canSerialize(owner: HolderOwner<T>) = delegate.canSerialize(owner)
}