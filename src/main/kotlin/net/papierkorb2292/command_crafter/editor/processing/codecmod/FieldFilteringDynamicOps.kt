package net.papierkorb2292.command_crafter.editor.processing.codecmod

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.MapLike
import net.papierkorb2292.command_crafter.editor.processing.DelegatingDynamicOps
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.stream.Stream

class FieldFilteringDynamicOps<T>(override val delegate: DynamicOps<T>, val fieldBlacklist: Set<kotlin.Pair<T, T>>) : DelegatingDynamicOps<T> {
    override fun getMap(input: T): DataResult<MapLike<T>> {
        return delegate.getMap(input).map { mapLike -> object : MapLike<T> {
            override fun get(key: T): T? =
                if(input to key !in fieldBlacklist) mapLike.get(key) else null

            override fun get(key: String): T? =
                if(input to delegate.createString(key) !in fieldBlacklist) mapLike.get(key) else null

            override fun entries(): Stream<Pair<T, T>> =
                mapLike.entries().filter { pair -> input to pair.first !in fieldBlacklist }
        } }
    }

    override fun getMapValues(input: T): DataResult<Stream<Pair<T, T>>> {
        return delegate.getMapValues(input).map { stream ->
            stream.filter { pair -> input to pair.first !in fieldBlacklist }
        }
    }

    override fun getMapEntries(input: T): DataResult<Consumer<BiConsumer<T, T>>> {
        return delegate.getMapEntries(input).map { entrySupplier -> Consumer{ entryConsumer ->
            entrySupplier.accept { key, value ->
                if(input to key !in fieldBlacklist)
                    entryConsumer.accept(key, value)
            }
        } }
    }
}