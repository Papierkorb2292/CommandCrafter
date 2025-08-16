package net.papierkorb2292.command_crafter.parser.helper

import com.fasterxml.jackson.annotation.JsonIgnore
import com.mojang.brigadier.context.CommandContext
import com.mojang.datafixers.util.Either
import com.mojang.datafixers.util.Pair
import net.minecraft.command.argument.CommandFunctionArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved.CommandFunctionArgumentTypeAccessor

class MutableFunctionArgument(val isTag: Boolean): CommandFunctionArgumentType.FunctionArgument {

    var id: Identifier? = null
    @JsonIgnore
    val idSetter: (Identifier) -> Unit = { id = it }

    override fun getFunctions(context: CommandContext<ServerCommandSource>): Collection<CommandFunction<ServerCommandSource>> {
        id.let {
            requireNotNull(it)
            return if(isTag)
                CommandFunctionArgumentTypeAccessor.callGetFunctionTag(context,it)
            else
                setOf(CommandFunctionArgumentTypeAccessor.callGetFunction(context, it))
        }
    }

    override fun getFunctionOrTag(context: CommandContext<ServerCommandSource?>?): Pair<Identifier?, Either<CommandFunction<ServerCommandSource>, Collection<CommandFunction<ServerCommandSource>>>?>? {
        id.let {
            requireNotNull(it)
            return Pair.of(it,
                if (isTag)
                    Either.right(CommandFunctionArgumentTypeAccessor.callGetFunctionTag(context, it))
                else
                    Either.left(CommandFunctionArgumentTypeAccessor.callGetFunction(context, it))
            )
        }
    }

    override fun getIdentifiedFunctions(context: CommandContext<ServerCommandSource>?): Pair<Identifier, Collection<CommandFunction<ServerCommandSource>>> {
        id.let {
            requireNotNull(it)
            return Pair.of(it, if(isTag)
                CommandFunctionArgumentTypeAccessor.callGetFunctionTag(context,it)
            else
                setOf(CommandFunctionArgumentTypeAccessor.callGetFunction(context, it)))
        }
    }
}