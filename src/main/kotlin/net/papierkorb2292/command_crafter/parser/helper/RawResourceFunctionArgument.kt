package net.papierkorb2292.command_crafter.parser.helper

import com.mojang.brigadier.context.CommandContext
import com.mojang.datafixers.util.Either
import com.mojang.datafixers.util.Pair
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.util.Identifier

class RawResourceFunctionArgument(val resource: RawResource): CommandFunctionArgumentType.FunctionArgument {
    override fun getFunctions(context: CommandContext<ServerCommandSource>?): MutableCollection<CommandFunction> {
        throw IllegalStateException("Tried to execute RawResource function argument")
    }

    override fun getFunctionOrTag(context: CommandContext<ServerCommandSource>?): Pair<Identifier, Either<CommandFunction, MutableCollection<CommandFunction>>> {
        throw IllegalStateException("Tried to execute RawResource function argument")
    }

}