package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

@Mixin(CommandFunctionArgumentType.class)
public interface CommandFunctionArgumentTypeAccessor {

    @SuppressWarnings({"RedundantThrows", "unused"})
    @Invoker
    static Collection<CommandFunction<ServerCommandSource>> callGetFunctionTag(@SuppressWarnings("unused") CommandContext<ServerCommandSource> context, Identifier id) throws CommandSyntaxException {
        throw new AssertionError();
    }

    @SuppressWarnings({"unused", "RedundantThrows"})
    @Invoker
    static CommandFunction<ServerCommandSource> callGetFunction(@SuppressWarnings("unused") CommandContext<ServerCommandSource> context, Identifier id) throws CommandSyntaxException {
        throw new AssertionError();
    }
}
