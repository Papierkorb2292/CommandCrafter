package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.datafixers.util.Either
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.core.HolderLookup
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.HolderOwner
import net.minecraft.tags.TagKey
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource
import java.util.*
import java.util.stream.Stream

class GeneratedRegistryEntryList<T: Any>(val registry: HolderLookup.RegistryLookup<T>): HolderSet<T> {
    @Suppress("UNCHECKED_CAST")
    val idSetter: (Identifier) -> Unit = {
        delegate = registry.getOrThrow(TagKey.create(registry.key() as ResourceKey<out Registry<T>>, it))
    }
    private var delegate: HolderSet<T>? = null
    private fun getNonNullDelegate(): HolderSet<T> = delegate ?: throw IllegalStateException("Generated registry entry list was used before the id was set")

    override fun iterator(): MutableIterator<Holder<T>> = getNonNullDelegate().iterator()
    override fun stream(): Stream<Holder<T>> = getNonNullDelegate().stream()
    override fun size(): Int = getNonNullDelegate().size()
    override fun isBound(): Boolean = getNonNullDelegate().isBound

    override fun unwrap(): Either<TagKey<T>, MutableList<Holder<T>>> = getNonNullDelegate().unwrap()
    override fun getRandomElement(random: RandomSource): Optional<Holder<T>> = getNonNullDelegate().getRandomElement(random)
    override fun get(index: Int): Holder<T> = getNonNullDelegate().get(index)
    override fun unwrapKey(): Optional<TagKey<T>> = getNonNullDelegate().unwrapKey()
    override fun canSerializeIn(owner: HolderOwner<T>): Boolean = getNonNullDelegate().canSerializeIn(owner)
    override fun contains(entry: Holder<T>): Boolean = getNonNullDelegate().contains(entry)
}