package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.TraceCallbacks;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(CustomCommandExecutor.WithErrorHandling.class)
public class ControlFlowAwareHelperMixin<T extends ExecutionCommandSource<T>> {
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
            method = "run(Lnet/minecraft/commands/ExecutionCommandSource;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/commands/execution/ChainModifiers;Lnet/minecraft/commands/execution/ExecutionControl;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/CustomCommandExecutor$WithErrorHandling;onError(Lcom/mojang/brigadier/exceptions/CommandSyntaxException;Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ChainModifiers;Lnet/minecraft/commands/execution/TraceCallbacks;)V"
            )
    )
    private void command_crafter$delayErrorWhenDebuggingUntilAfterPauseCheck(CustomCommandExecutor.WithErrorHandling<T> instance, CommandSyntaxException exception, T source, ChainModifiers flags, @Nullable TraceCallbacks tracer, Operation<Void> op, T abstractServerCommandSource, ContextChain<T> contextChain, ChainModifiers executionFlags, ExecutionControl<T> executionControl) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext == null) return;

        // This action will happen after any other actions that the command queued which might pause the debugger
        executionControl.queueNext((control, frame) -> {
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
