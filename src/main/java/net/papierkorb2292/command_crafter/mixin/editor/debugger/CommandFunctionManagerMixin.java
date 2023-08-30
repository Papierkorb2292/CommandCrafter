package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.CommandFunctionManager;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionPausedThrowable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandFunctionManager.class)
public class CommandFunctionManagerMixin {

    @WrapOperation(
            method = "executeAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/function/CommandFunctionManager;execute(Lnet/minecraft/server/function/CommandFunction;Lnet/minecraft/server/command/ServerCommandSource;)I"
            )
    )
    private int command_crafter$catchExecutionPausedThrowable(CommandFunctionManager manager, CommandFunction function, ServerCommandSource source, Operation<Integer> op) {
        try {
            return MixinUtil.<Integer, ExecutionPausedThrowable>callWithThrows(op, manager, function, source);
        } catch (ExecutionPausedThrowable ignored) {
            return 0;
        }
    }
}
