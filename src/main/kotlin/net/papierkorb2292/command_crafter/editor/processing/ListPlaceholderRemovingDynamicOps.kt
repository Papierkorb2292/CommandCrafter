package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import java.util.function.Consumer

class ListPlaceholderRemovingDynamicOps<T>(private val placeholders: Set<T>, override val delegate: DynamicOps<T>): DelegatingDynamicOps<T> {
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
}