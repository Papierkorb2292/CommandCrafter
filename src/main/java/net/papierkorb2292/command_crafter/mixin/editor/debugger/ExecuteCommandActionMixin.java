package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.tasks.ExecuteCommand;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExecuteCommand.class)
public class ExecuteCommandActionMixin<T extends ExecutionCommandSource<T>> {

    @Shadow @Final private CommandContext<T> executionContext;

    @Inject(
            method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V",
            at = @At("HEAD")
    )
    private void command_crafter$initAndCheckPause(T abstractServerCommandSource, ExecutionContext<T> commandExecutionContext, Frame frame, CallbackInfo ci) {
        if (!(abstractServerCommandSource instanceof CommandSourceStack source)) return;
        //noinspection unchecked
        FunctionDebugFrame.Companion.checkSimpleActionPause((CommandContext<CommandSourceStack>) executionContext, source, null);
    }
}
