package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.datafixers.util.Either
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.registry.entry.RegistryEntryOwner
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.random.Random
import java.util.*
import java.util.stream.Stream

class GeneratedRegistryEntryList<T>(val registry: Registry<T>): RegistryEntryList<T> {
    val idSetter: (Identifier) -> Unit = {
        delegate = registry.getEntryList(TagKey.of(registry.key, it)).orElseThrow()
    }
    private var delegate: RegistryEntryList<T>? = null
    private fun getNonNullDelegate(): RegistryEntryList<T> = delegate ?: throw IllegalStateException("Generated registry entry list was used before the id was set")

    override fun iterator(): MutableIterator<RegistryEntry<T>> = getNonNullDelegate().iterator()
    override fun stream(): Stream<RegistryEntry<T>> = getNonNullDelegate().stream()
    override fun size(): Int = getNonNullDelegate().size()
    override fun getStorage(): Either<TagKey<T>, MutableList<RegistryEntry<T>>> = getNonNullDelegate().storage
    override fun getRandom(random: Random?): Optional<RegistryEntry<T>> = getNonNullDelegate().getRandom(random)
    override fun get(index: Int): RegistryEntry<T> = getNonNullDelegate().get(index)
    override fun getTagKey(): Optional<TagKey<T>> = getNonNullDelegate().tagKey
    override fun ownerEquals(owner: RegistryEntryOwner<T>?): Boolean = getNonNullDelegate().ownerEquals(owner)
    override fun contains(entry: RegistryEntry<T>?): Boolean = getNonNullDelegate().contains(entry)
}