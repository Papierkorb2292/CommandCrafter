package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.command.CommandFunctionAction;
import net.minecraft.command.CommandQueueEntry;
import net.minecraft.command.Frame;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.Procedure;
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionContextContinueCallback;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ExitDebugFrameCommandAction;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.parser.helper.FileLinesContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Mixin(CommandFunctionAction.class)
public class CommandFunctionActionMixin<T extends AbstractServerCommandSource<T>> {

    @Shadow @Final private Procedure<T> function;

    @Inject(
            method = "execute(Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/command/CommandExecutionContext;Lnet/minecraft/command/Frame;)V",
            at = @At("RETURN")
    )
    private void command_crafter$createFunctionDebugFrame(T abstractServerCommandSource, CommandExecutionContext<T> commandExecutionContext, Frame frame, CallbackInfo ci) {
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if (pauseContext == null || !(function instanceof DebugInformationContainer<?,?> container))
            return;
        //noinspection unchecked
        var debugInformation = ((DebugInformationContainer<?, FunctionDebugFrame>) container).command_crafter$getDebugInformation();
        if (debugInformation == null)
            return;
        var lines = ((FileLinesContainer)function).command_crafter$getLines();
        var fileLines = new HashMap<String, List<String>>();
        if (lines != null) {
            fileLines.put(PackContentFileType.Companion.getFunctionsFileType().toStringPath(function.id()), lines);
        }
        //noinspection unchecked
        var debugFrame = new FunctionDebugFrame(
                pauseContext,
                (Procedure<ServerCommandSource>) function,
                debugInformation,
                Collections.emptyList(), //TODO
                Collections.emptyList(), //TODO
                new CommandExecutionContextContinueCallback(commandExecutionContext),
                fileLines
        );
        pauseContext.getExecutionWrapper().addTo(debugFrame.getExecutionWrapper());
        pauseContext.pushDebugFrame(debugFrame);
        //noinspection unchecked
        commandExecutionContext.enqueueCommand((CommandQueueEntry<T>) new CommandQueueEntry<>(frame, ExitDebugFrameCommandAction.INSTANCE));
    }

    @ModifyExpressionValue(
            method = "execute(Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/command/CommandExecutionContext;Lnet/minecraft/command/Frame;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/Frame;frameControl()Lnet/minecraft/command/Frame$Control;"
            )
    )
    private Frame.Control command_crafter$exitDebugFrameOnPropagatedReturn(Frame.Control control) {
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if (pauseContext == null || !(function instanceof DebugInformationContainer<?,?> container) || container.command_crafter$getDebugInformation() == null)
            return control;
        return () -> {
            pauseContext.popDebugFrame();
            control.discard();
        };
    }
}
