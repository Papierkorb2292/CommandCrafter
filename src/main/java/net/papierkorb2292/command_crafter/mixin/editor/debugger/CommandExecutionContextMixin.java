package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kotlin.Unit;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.command.CommandQueueEntry;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionCompletedFutureProvider;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ExitDebugFrameCommandAction;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandExecutionContext.class)
public class CommandExecutionContextMixin<T> implements ExecutionCompletedFutureProvider {

    private CommandQueueEntry<T> command_crafter$currentCommandQueueEntry;

    private final CompletableFuture<Unit> command_crafter$executionCompletedFuture = new CompletableFuture<>();

    @WrapOperation(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/CommandQueueEntry;execute(Lnet/minecraft/command/CommandExecutionContext;)V"
            )
    )
    public void command_crafter$setCommandQueueEntry(CommandQueueEntry<T> commandQueueEntry, CommandExecutionContext<T> context, Operation<Void> op) {
        if(!(commandQueueEntry.action() instanceof ExitDebugFrameCommandAction))
            command_crafter$currentCommandQueueEntry = commandQueueEntry;
        op.call(commandQueueEntry, context);
        command_crafter$currentCommandQueueEntry = null;
    }

    @WrapWithCondition(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/CommandExecutionContext;queuePendingCommands()V"
            )
    )
    public boolean command_crafter$skipQueueOnContinueExecution(CommandExecutionContext<T> instance) {
        return command_crafter$currentCommandQueueEntry == null;
    }

    @WrapOperation(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Deque;pollFirst()Ljava/lang/Object;"
            )
    )
    public Object command_crafter$usePrevEntryOnContinueExecution(Deque<CommandQueueEntry<T>> queue, Operation<Object> op) {
        return command_crafter$currentCommandQueueEntry != null
                ? command_crafter$currentCommandQueueEntry
                : op.call(queue);
    }

    @ModifyExpressionValue(
            method = "run",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/command/CommandExecutionContext;commandsRemaining:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int command_crafter$skipInsufficientCommandsRemainingOnContinueExecution(int commandsRemaining) {
        return command_crafter$currentCommandQueueEntry != null ? 1 : commandsRemaining;
    }

    @Inject(
        method = "run",
        at = @At("RETURN")
    )
    private void command_crafter$completeFuture(CallbackInfo ci) {
        command_crafter$executionCompletedFuture.complete(Unit.INSTANCE);
    }

    @NotNull
    @Override
    public CompletableFuture<Unit> command_crafter$getExecutionCompletedFuture() {
        return command_crafter$executionCompletedFuture;
    }
}
