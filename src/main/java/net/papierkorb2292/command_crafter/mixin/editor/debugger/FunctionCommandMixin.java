package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.command.CommandAction;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.FunctionCommand;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FunctionCallDebugInfo;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(FunctionCommand.class)
public class FunctionCommandMixin {

    private static final ThreadLocal<Integer> command_crafter$tagEntryIndex = new ThreadLocal<>();

    @ModifyArg(
            method = "enqueueFunction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/ExecutionControl;enqueueAction(Lnet/minecraft/command/CommandAction;)V"
            )
    )
    private static <T extends AbstractServerCommandSource<T>> CommandAction<T> command_crafter$addPauseToCommandFunctionAction(CommandAction<T> action) {
        var tagWrappedAction = FunctionTagDebugFrame.Companion.wrapCommandActionWithTagPauseCheck(action, command_crafter$getNextTagEntryIndex());

        FunctionCallDebugInfo debugInfo = FunctionDebugFrame.Companion.getFunctionCallDebugInfo().get();
        if (debugInfo != null) {
            return (context, frame) -> {
                FunctionDebugFrame.Companion.checkSimpleActionPause(debugInfo.getContext(), debugInfo.getSource(), debugInfo.getCommandInfo());
                tagWrappedAction.execute(context, frame);
            };
        }
        return tagWrappedAction;
    }

    @ModifyExpressionValue(
            method = "enqueueOutsideReturnRun",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z"
            )
    )
    private static boolean command_crafter$countTagEntryIndex(boolean hasNext) {
        if(hasNext) command_crafter$tagEntryIndex.set(command_crafter$getNextTagEntryIndex());
        else command_crafter$tagEntryIndex.remove();
        return hasNext;
    }

    private static int command_crafter$getNextTagEntryIndex() {
        Integer index = getOrNull(command_crafter$tagEntryIndex);
        return index != null ? index + 1 : 0;
    }
}
