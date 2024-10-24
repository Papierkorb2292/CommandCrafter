package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.datafixers.util.Either
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.entry.RegistryEntryList
import net.minecraft.registry.entry.RegistryEntryOwner
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.math.random.Random
import java.util.*
import java.util.stream.Stream

class RawResourceRegistryEntryList<T>(val resource: RawResource) : RegistryEntryList<T> {
    override fun iterator(): MutableIterator<RegistryEntry<T>> {
        throwOnUsage()
    }
    override fun stream(): Stream<RegistryEntry<T>> {
        throwOnUsage()
    }
    override fun size(): Int {
        throwOnUsage()
    }

    override fun isBound(): Boolean {
        throwOnUsage()
    }

    override fun getStorage(): Either<TagKey<T>, MutableList<RegistryEntry<T>>> {
        throwOnUsage()
    }
    override fun getRandom(random: Random?): Optional<RegistryEntry<T>> {
        throwOnUsage()
    }
    override fun get(index: Int): RegistryEntry<T> {
        throwOnUsage()
    }
    override fun getTagKey(): Optional<TagKey<T>> {
        throwOnUsage()
    }
    override fun ownerEquals(owner: RegistryEntryOwner<T>?): Boolean {
        throwOnUsage()
    }
    override fun contains(entry: RegistryEntry<T>?): Boolean {
        throwOnUsage()
    }
    private fun throwOnUsage(): Nothing = throw IllegalStateException("Tried to use RawResource registry entry list")
}