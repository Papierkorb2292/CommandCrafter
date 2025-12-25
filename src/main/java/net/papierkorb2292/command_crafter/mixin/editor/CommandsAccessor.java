package net.papierkorb2292.command_crafter.mixin.editor;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(Commands.class)
public interface CommandsAccessor {
    @Accessor
    static ThreadLocal<ExecutionContext<CommandSourceStack>> getCURRENT_EXECUTION_CONTEXT() {
        throw new AssertionError();
    }

    @Invoker
    static <S> void callFillUsableCommands(CommandNode<S> tree, CommandNode<S> result, S source, Map<CommandNode<S>, CommandNode<S>> resultNodes) {
        throw new AssertionError();
    }
}
