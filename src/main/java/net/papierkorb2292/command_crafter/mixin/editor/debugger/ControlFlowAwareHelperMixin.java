package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.ControlFlowAware;
import net.minecraft.command.ExecutionControl;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.function.Tracer;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ControlFlowAware.Helper.class)
public class ControlFlowAwareHelperMixin<T extends AbstractServerCommandSource<T>> {
    /**
     * This function can delay sending an error until after the debugger checked whether it should pause at this command.
     * That is done because if the errors were always sent immediately, users will not see them if the pause is triggered by a breakpoint
     * on this command, since command feedback will only be logged after the first breakpoint is hit. Additionally, it would be unintuitive if errors
     * from a command are logged before the user stepped over them.
     * <br>
     * The way this is implemented does send the error multiple times when debugging started with this command and no times when debugging ended with this command,
     * but I don't think it matters that much
     */
    @WrapOperation(
            method = "execute(Lnet/minecraft/server/command/AbstractServerCommandSource;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/command/ExecutionFlags;Lnet/minecraft/command/ExecutionControl;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/ControlFlowAware$Helper;sendError(Lcom/mojang/brigadier/exceptions/CommandSyntaxException;Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/command/ExecutionFlags;Lnet/minecraft/server/function/Tracer;)V"
            )
    )
    private void command_crafter$delayErrorWhenDebuggingUntilAfterPauseCheck(ControlFlowAware.Helper<T> instance, CommandSyntaxException exception, T source, ExecutionFlags flags, @Nullable Tracer tracer, Operation<Void> op, T abstractServerCommandSource, ContextChain<T> contextChain, ExecutionFlags executionFlags, ExecutionControl<T> executionControl) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext == null) return;

        // This action will happen after any other actions that the command queued which might pause the debugger
        executionControl.enqueueAction((control, frame) -> {
            if(pauseContext.isDebugging()) {
                op.call(instance, exception, source, flags, tracer);
            }
        });

        // Don't send the error immediately if the user is debugging to avoid duplicate logs
        if(!pauseContext.isDebugging()) {
            op.call(instance, exception, source, flags, tracer);
        }
    }
}
