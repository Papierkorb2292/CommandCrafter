package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.command.FixedCommandAction;
import net.minecraft.command.Frame;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FixedCommandAction.class)
public class FixedCommandActionMixin<T extends AbstractServerCommandSource<T>> {

    @Shadow @Final private CommandContext<T> context;

    @Inject(
            method = "execute(Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/command/CommandExecutionContext;Lnet/minecraft/command/Frame;)V",
            at = @At("HEAD")
    )
    private void command_crafter$initAndCheckPause(T abstractServerCommandSource, CommandExecutionContext<T> commandExecutionContext, Frame frame, CallbackInfo ci) {
        if (!(abstractServerCommandSource instanceof ServerCommandSource source)) return;
        //noinspection unchecked
        FunctionDebugFrame.Companion.checkSimpleActionPause((CommandContext<ServerCommandSource>) context, source, null);
    }
}
