package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionCompletedFutureProvider;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionPausedThrowable;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Shadow @Final private static ThreadLocal<ExecutionContext<CommandSourceStack>> CURRENT_EXECUTION_CONTEXT;

    @Inject(
            method = "executeCommandInContext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/ExecutionContext;runCommandQueue()V"
            )
    )
    private static void command_crafter$createPauseContext(CommandSourceStack commandSource, Consumer<ExecutionContext<CommandSourceStack>> callback, CallbackInfo ci, @Share("resetContext") LocalBooleanRef resetContextRef) {
        resetContextRef.set(PauseContext.Companion.trySetUpPauseContext(() -> new PauseContext(commandSource.getServer(), null, false)));
    }

    @Inject(
            method = "executeCommandInContext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/ExecutionContext;close()V",
                    ordinal = 0
            )
    )
    private static void command_crafter$resetPauseContextWithoutException(CommandSourceStack commandSource, Consumer<ExecutionContext<CommandSourceStack>> callback, CallbackInfo ci, @Share("resetContext") LocalBooleanRef resetContextRef) {
        if (resetContextRef.get()) {
            PauseContext.Companion.resetPauseContext();
        }
    }

    @Inject(
            method = "executeCommandInContext",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/ExecutionContext;close()V",
                    ordinal = 1
            ),
            cancellable = true
    )
    private static void command_crafter$resetPauseContextWithException(CommandSourceStack commandSource, Consumer<ExecutionContext<CommandSourceStack>> callback, CallbackInfo ci, @Share("resetContext") LocalBooleanRef resetContextRef, @Local Throwable throwable) throws Throwable {
        var resetContext = resetContextRef.get();
        if(throwable instanceof ExecutionPausedThrowable paused) {
            var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
            var executionContext = CURRENT_EXECUTION_CONTEXT.get();
            paused.getWrapperConsumer().accept(inner -> {
                CURRENT_EXECUTION_CONTEXT.set(executionContext);
                if(resetContext)
                    PauseContext.Companion.getCurrentPauseContext().set(pauseContext);
                try {
                    inner.invoke();
                } finally {
                    if(resetContext)
                        PauseContext.Companion.resetPauseContext();
                    CURRENT_EXECUTION_CONTEXT.remove();
                }
            });
            ((ExecutionCompletedFutureProvider) executionContext)
                    .command_crafter$getExecutionCompletedFuture()
                    .thenRun(executionContext::close);
            if(resetContext) {
                CURRENT_EXECUTION_CONTEXT.remove();
                PauseContext.Companion.resetPauseContext();
                ci.cancel();
            }
            return;
        }
        if (resetContext) {
            PauseContext.Companion.resetPauseContext();
        }
    }
}
