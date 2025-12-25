package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import kotlin.Unit;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionCompletedFutureProvider;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ExitDebugFrameCommandAction;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(ExecutionContext.class)
public class ExecutionContextMixin<T> implements ExecutionCompletedFutureProvider {

    @Shadow @Final private Deque<CommandQueueEntry<T>> commandQueue;
    private CommandQueueEntry<T> command_crafter$currentCommandQueueEntry;

    private final CompletableFuture<Unit> command_crafter$executionCompletedFuture = new CompletableFuture<>();

    @WrapOperation(
            method = "runCommandQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/CommandQueueEntry;execute(Lnet/minecraft/commands/execution/ExecutionContext;)V"
            )
    )
    public void command_crafter$setCommandQueueEntry(CommandQueueEntry<T> commandQueueEntry, ExecutionContext<T> context, Operation<Void> op) {
        command_crafter$currentCommandQueueEntry = commandQueueEntry;
        op.call(commandQueueEntry, context);
        command_crafter$currentCommandQueueEntry = null;
    }

    @WrapWithCondition(
            method = "runCommandQueue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/ExecutionContext;pushNewCommands()V"
            )
    )
    public boolean command_crafter$skipQueueOnContinueExecution(ExecutionContext<T> instance) {
        return command_crafter$currentCommandQueueEntry == null;
    }

    @WrapOperation(
            method = "runCommandQueue",
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
            method = "runCommandQueue",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/commands/execution/ExecutionContext;commandQuota:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int command_crafter$skipInsufficientCommandsRemainingOnContinueExecution(int commandsRemaining) {
        return command_crafter$currentCommandQueueEntry != null ? 1 : commandsRemaining;
    }

    @Inject(
        method = "runCommandQueue",
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

    @ModifyExpressionValue(
            method = "discardAtDepthOrHigher",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Deque;removeFirst()Ljava/lang/Object;"
            )
    )
    private Object command_crafter$saveDiscardedExitDebugFrameActions(Object removed, @Share("removedExitActions") LocalRef<List<CommandQueueEntry<T>>> removedExitActions) {
        //noinspection unchecked
        var entry = (CommandQueueEntry<T>)removed;
        if(entry.action() instanceof ExitDebugFrameCommandAction || entry.action() == FunctionTagDebugFrame.Companion.getCOPY_TAG_RESULT_TO_COMMAND_RESULT_COMMAND_ACTION()) {
            if(removedExitActions.get() == null) {
                removedExitActions.set(new ArrayList<>());
            }
            removedExitActions.get().addFirst(entry);
        }
        return removed;
    }

    @Inject(
            method = "discardAtDepthOrHigher",
            at = @At("RETURN")
    )
    private void command_crafter$restoreDiscardedExitDebugFrameActions(CallbackInfo ci, @Share("removedExitActions") LocalRef<List<CommandQueueEntry<T>>> removedExitActions) {
        if (removedExitActions.get() == null) return;
        for (var action : removedExitActions.get()) {
            commandQueue.addFirst(action);
        }
    }
}
