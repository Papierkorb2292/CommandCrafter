package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.command.CommandAction;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.FunctionCommand;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FunctionCallDebugInfo;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(FunctionCommand.class)
public class FunctionCommandMixin {

    @ModifyArg(
            method = "enqueueFunction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/ExecutionControl;enqueueAction(Lnet/minecraft/command/CommandAction;)V"
            )
    )
    private static <T extends AbstractServerCommandSource<T>> CommandAction<T> command_crafter$addPauseToCommandFunctionAction(CommandAction<T> action) {
        FunctionCallDebugInfo debugInfo = FunctionDebugFrame.Companion.getFunctionCallDebugInfo().get();
        if (debugInfo != null) {
            return (context, frame) -> {
                FunctionDebugFrame.Companion.checkSimpleActionPause(debugInfo.getContext(), debugInfo.getSource(), debugInfo.getCommandInfo());
                action.execute(context, frame);
            };
        }
        return action;
    }
}
