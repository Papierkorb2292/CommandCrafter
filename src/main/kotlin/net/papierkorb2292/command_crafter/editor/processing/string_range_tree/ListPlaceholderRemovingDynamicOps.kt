package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import java.util.function.Consumer
import java.util.stream.Stream

class ListPlaceholderRemovingDynamicOps<T>(private val placeholders: Set<T>, override val delegate: DynamicOps<T>):
    DelegatingDynamicOps<T> {
    override fun getList(input: T): DataResult<Consumer<Consumer<T>>> {
        return delegate.getList(input).map { list ->
            Consumer { visitor ->
                list.accept {
                    if(it !in placeholders) {
                        visitor.accept(it)
                    }
                }
            }
        }
    }

    override fun getStream(input: T): DataResult<Stream<T>> =
        delegate.getStream(input).map { stream ->
            stream.filter { value -> value !in placeholders }
        }

    // Make sure getIntStream and stuff use the new getStream
    override val delegateTypedLists: Boolean
        get() = false
}