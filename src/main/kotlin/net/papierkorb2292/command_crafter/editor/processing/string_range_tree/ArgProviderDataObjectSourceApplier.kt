package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.commands.ArgProvider
import java.util.function.Function

/**
 * Wraps an ArgProvider such that additional data can be attached to the command tree.
 * For example used to attach a DataObjectSource to the compound or path argument in /data merge|modify
 */
class ArgProviderDataObjectSourceApplier<T : Any>(val delegate: ArgProvider<T>, val dataObjectSourceProvider: Modifier) : ArgProvider<T> {
    companion object {
        fun <T : Any> wrapFactory(
            factory: ArgProvider.Factory<T>,
            dataObjectSourceProvider: Modifier,
        ): ArgProvider.Factory<T> =
            { key -> ArgProviderDataObjectSourceApplier(factory.create(key), dataObjectSourceProvider) }
    }

    override fun access(context: CommandContext<CommandSourceStack>): T =
        delegate.access(context)

    override fun wrap(
        parent: ArgumentBuilder<CommandSourceStack, *>,
        function: Function<ArgumentBuilder<CommandSourceStack, *>, ArgumentBuilder<CommandSourceStack, *>>,
    ): ArgumentBuilder<CommandSourceStack, *> {
        return delegate.wrap(parent) { arg ->
            val tree = function.apply(arg)
            dataObjectSourceProvider.modify(tree, parent)
            tree
        }
    }

    fun interface Modifier {
        fun modify(argument: ArgumentBuilder<CommandSourceStack, *>, parent: ArgumentBuilder<CommandSourceStack, *>)
    }
}