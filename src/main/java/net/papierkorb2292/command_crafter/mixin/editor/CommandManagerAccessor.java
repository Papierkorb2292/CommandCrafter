package net.papierkorb2292.command_crafter.mixin.editor;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(CommandManager.class)
public interface CommandManagerAccessor {
    @Accessor
    static ThreadLocal<CommandExecutionContext<ServerCommandSource>> getCURRENT_CONTEXT() {
        throw new AssertionError();
    }

    @Invoker
    static <S> void callMakeTreeForSource(CommandNode<S> tree, CommandNode<S> result, S source, Map<CommandNode<S>, CommandNode<S>> resultNodes) {
        throw new AssertionError();
    }
}
