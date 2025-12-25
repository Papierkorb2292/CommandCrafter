package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.datafixers.util.Either
import com.mojang.datafixers.util.Pair
import net.minecraft.commands.arguments.item.FunctionArgument
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.functions.CommandFunction
import net.minecraft.resources.Identifier

class RawResourceFunctionArgument(val resource: RawResource, val isTag: Boolean = false): FunctionArgument.Result {
    override fun create(context: CommandContext<CommandSourceStack>): MutableCollection<CommandFunction<CommandSourceStack>> {
        throw IllegalStateException("Tried to execute RawResource function argument")
    }

    override fun unwrap(context: CommandContext<CommandSourceStack>): Pair<Identifier, Either<CommandFunction<CommandSourceStack>, MutableCollection<CommandFunction<CommandSourceStack>>>> {
        throw IllegalStateException("Tried to execute RawResource function argument")
    }

    override fun unwrapToCollection(context: CommandContext<CommandSourceStack>): Pair<Identifier, MutableCollection<CommandFunction<CommandSourceStack>>> {
        throw IllegalStateException("Tried to execute RawResource function argument")
    }

}