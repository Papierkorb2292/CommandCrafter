package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.command.FixedCommandAction;
import net.minecraft.command.Frame;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
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
            method = "<init>",
            at = @At("RETURN")
    )
    private void command_crafter$initCommandInfo(String command, ExecutionFlags flags, CommandContext<T> context, CallbackInfo ci, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        if (!(context.getSource() instanceof ServerCommandSource)) return;
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if(pauseContext == null) return;
        var debugFrame = pauseContext.peekDebugFrame();
        if(!(debugFrame instanceof FunctionDebugFrame functionDebugFrame)) return;
        //noinspection unchecked
        commandInfoRef.set(functionDebugFrame.getCommandInfo((CommandContext<ServerCommandSource>)context));
        debugFrameRef.set(functionDebugFrame);
    }

    @Inject(
            method = "execute(Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/command/CommandExecutionContext;Lnet/minecraft/command/Frame;)V",
            at = @At("HEAD")
    )
    private void command_crafter$initAndCheckPause(T abstractServerCommandSource, CommandExecutionContext<T> commandExecutionContext, Frame frame, CallbackInfo ci, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return;
        debugFrame.checkPause(commandInfoRef.get(), 0, context, (ServerCommandSource) abstractServerCommandSource);
        var sectionSources = debugFrame.getCurrentSectionSources();
        sectionSources.setCurrentSourceIndex(sectionSources.getCurrentSourceIndex() + 1);
    }
}
