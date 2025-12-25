package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.server.commands.FunctionCommand;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(FunctionCommand.class)
public class FunctionCommandMixin {

    private static final ThreadLocal<Integer> command_crafter$tagEntryIndex = new ThreadLocal<>();

    @ModifyArg(
            method = "instantiateAndQueueFunctions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/ExecutionControl;queueNext(Lnet/minecraft/commands/execution/EntryAction;)V"
            )
    )
    private static <T extends ExecutionCommandSource<T>> EntryAction<T> command_crafter$addPauseToCommandFunctionAction(EntryAction<T> action) {
        var entryIndex = getOrNull(command_crafter$tagEntryIndex);

        return FunctionTagDebugFrame.Companion.wrapCommandActionWithTagPauseCheck(action, entryIndex == null ? 0 : entryIndex);
    }

    @ModifyExpressionValue(
            method = "queueFunctionsNoReturn",
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
