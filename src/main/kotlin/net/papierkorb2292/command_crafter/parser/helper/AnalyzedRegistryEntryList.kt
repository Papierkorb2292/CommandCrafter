package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.datafixers.util.Either
import net.minecraft.core.Holder
import net.minecraft.core.HolderSet
import net.minecraft.core.HolderOwner
import net.minecraft.tags.TagKey
import net.minecraft.util.RandomSource
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import java.util.*
import java.util.stream.Stream

class AnalyzedRegistryEntryList<T: Any>(val analyzingResult: AnalyzingResult) : HolderSet<T> {
    override fun iterator(): MutableIterator<Holder<T>> {
        throwOnUsage()
    }
    override fun stream(): Stream<Holder<T>> {
        throwOnUsage()
    }
    override fun size(): Int {
        throwOnUsage()
    }

    override fun isBound(): Boolean {
        throwOnUsage()
    }

    override fun unwrap(): Either<TagKey<T>, MutableList<Holder<T>>> {
        throwOnUsage()
    }
    override fun getRandomElement(random: RandomSource): Optional<Holder<T>> {
        throwOnUsage()
    }
    override fun get(index: Int): Holder<T> {
        throwOnUsage()
    }
    override fun unwrapKey(): Optional<TagKey<T>> {
        throwOnUsage()
    }
    override fun canSerializeIn(owner: HolderOwner<T>): Boolean {
        throwOnUsage()
    }
    override fun contains(entry: Holder<T>): Boolean {
        throwOnUsage()
    }
    private fun throwOnUsage(): Nothing = throw IllegalStateException("Tried to use analyzed registry entry list")
}