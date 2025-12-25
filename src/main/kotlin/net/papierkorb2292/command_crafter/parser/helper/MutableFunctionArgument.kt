package net.papierkorb2292.command_crafter.parser.helper

import com.fasterxml.jackson.annotation.JsonIgnore
import com.mojang.brigadier.context.CommandContext
import com.mojang.datafixers.util.Either
import com.mojang.datafixers.util.Pair
import net.minecraft.commands.arguments.item.FunctionArgument
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.functions.CommandFunction
import net.minecraft.resources.Identifier
import net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved.FunctionArgumentAccessor

class MutableFunctionArgument(val isTag: Boolean): FunctionArgument.Result {

    var id: Identifier? = null
    @JsonIgnore
    val idSetter: (Identifier) -> Unit = { id = it }

    override fun create(context: CommandContext<CommandSourceStack>): Collection<CommandFunction<CommandSourceStack>> {
        id.let {
            requireNotNull(it)
            return if(isTag)
                FunctionArgumentAccessor.callGetFunctionTag(context,it)
            else
                setOf(FunctionArgumentAccessor.callGetFunction(context, it))
        }
    }

    override fun unwrap(context: CommandContext<CommandSourceStack>): Pair<Identifier, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> {
        id.let {
            requireNotNull(it)
            return Pair.of(it,
                if (isTag)
                    Either.right(FunctionArgumentAccessor.callGetFunctionTag(context, it))
                else
                    Either.left(FunctionArgumentAccessor.callGetFunction(context, it))
            )
        }
    }

    override fun unwrapToCollection(context: CommandContext<CommandSourceStack>): Pair<Identifier, Collection<CommandFunction<CommandSourceStack>>> {
        id.let {
            requireNotNull(it)
            return Pair.of(it, if(isTag)
                FunctionArgumentAccessor.callGetFunctionTag(context,it)
            else
                setOf(FunctionArgumentAccessor.callGetFunction(context, it)))
        }
    }
}