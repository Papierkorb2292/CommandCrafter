package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

@Mixin(FunctionArgument.class)
public interface FunctionArgumentAccessor {

    @SuppressWarnings({"RedundantThrows", "unused"})
    @Invoker
    static Collection<CommandFunction<CommandSourceStack>> callGetFunctionTag(@SuppressWarnings("unused") CommandContext<CommandSourceStack> context, Identifier id) throws CommandSyntaxException {
        throw new AssertionError();
    }

    @SuppressWarnings({"unused", "RedundantThrows"})
    @Invoker
    static CommandFunction<CommandSourceStack> callGetFunction(@SuppressWarnings("unused") CommandContext<CommandSourceStack> context, Identifier id) throws CommandSyntaxException {
        throw new AssertionError();
    }
}
