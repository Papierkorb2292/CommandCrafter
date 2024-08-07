package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.command.CommandAction;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.FunctionCommand;
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
        var entryIndex = getOrNull(command_crafter$tagEntryIndex);

        return FunctionTagDebugFrame.Companion.wrapCommandActionWithTagPauseCheck(action, entryIndex == null ? 0 : entryIndex);
    }

    @ModifyExpressionValue(
            method = "enqueueOutsideReturnRun",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;hasNext()Z"
            )
    )
    private static boolean command_crafter$countTagEntryIndex(boolean hasNext) {
        if(hasNext) {
            Integer index = getOrNull(command_crafter$tagEntryIndex);
            command_crafter$tagEntryIndex.set(index != null ? index + 1 : 0);
        }
        else command_crafter$tagEntryIndex.remove();
        return hasNext;
    }
}
