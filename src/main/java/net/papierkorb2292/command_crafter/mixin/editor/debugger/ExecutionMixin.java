package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.Unit;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.CommandFunctionManager;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionPausedThrowable;
import net.papierkorb2292.command_crafter.editor.debugger.server.FunctionDebugHandler;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionPauseContextImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Deque;

@SuppressWarnings("unused")
@Mixin(CommandFunctionManager.Execution.class)
public abstract class ExecutionMixin {

    @Shadow @Final private Deque<CommandFunctionManager.Entry> queue;
    @Shadow @Final @Nullable private CommandFunctionManager.@Nullable Tracer tracer;

    @Shadow abstract int run(CommandFunction function, ServerCommandSource source) throws ExecutionPausedThrowable;

    @Shadow private int depth;
    private FunctionPauseContextImpl command_crafter$pauseContext;

    @Inject(
            method = "run",
            at = @At("HEAD")
    )
    private void command_crafter$continuePrevCommandExecAfterPause(CommandFunction function, ServerCommandSource source, CallbackInfoReturnable<Integer> cir) throws ExecutionPausedThrowable {
        var pauseContext = command_crafter$pauseContext;
        if (pauseContext == null) {
            return;
        }
        var executedEntries = pauseContext.getExecutedEntries();
        queue.push(executedEntries.get(executedEntries.size() - 1));
        executedEntries.remove(executedEntries.size() - 1);
    }

    @ModifyReceiver(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/function/CommandFunctionManager$Entry;execute(Lnet/minecraft/server/function/CommandFunctionManager;Ljava/util/Deque;ILnet/minecraft/server/function/CommandFunctionManager$Tracer;)V"
            )
    )
    private CommandFunctionManager.Entry command_crafter$createPauseContextAndAppendToExecutedEntriesAndPauseOnFunctionChange(CommandFunctionManager.Entry entry, CommandFunctionManager manager, Deque<CommandFunctionManager.Entry> queue, int maxCommandChainLength, CommandFunctionManager.Tracer tracer, CommandFunction function) throws ExecutionPausedThrowable {
        var pauseContext = command_crafter$pauseContext;
        var element = ((CommandFunctionManagerEntryAccessor) entry).getElement();
        if (element instanceof CommandFunction.CommandElement commandElement) {
            if (pauseContext == null) {
                pauseContext = new FunctionPauseContextImpl(queue, commandElement, manager, (context) -> {
                    try {
                        //noinspection DataFlowIssue
                        ((CommandFunctionManagerAccessor) manager).setExecution((CommandFunctionManager.Execution) (Object) this);
                        var result = run(null, null);
                        ((CommandFunctionManagerAccessor)manager).setExecution(null);
                        context.getFunctionCompletionFuture().complete(result);
                    } catch (ExecutionPausedThrowable ignored) {
                        ((CommandFunctionManagerAccessor)manager).setExecution(null);
                    } catch (Throwable throwable) {
                        ((CommandFunctionManagerAccessor)manager).setExecution(null);
                        context.getFunctionCompletionFuture().completeExceptionally(throwable);
                    }
                    return Unit.INSTANCE;
                });
                pauseContext.onFunctionEnter(function);
                command_crafter$pauseContext = pauseContext;
            }
            FunctionDebugHandler.Companion.getCurrentPauseContext().set(pauseContext);
        }
        if(pauseContext != null) {
            var newDepth = ((CommandFunctionManagerEntryAccessor) entry).getDepth();
            if(newDepth > depth) {
                pauseContext.onFunctionDepthIncreased();
            }
            if(newDepth < depth) {
                pauseContext.onFunctionExit();
            }

            pauseContext.getExecutedEntries().add(entry);
        }
        return entry;
    }

    @Inject(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;pop()V",
                    ordinal = 0 //The 'finally' block when no exception was thrown
            )
    )
    private void command_crafter$resetPauseContextIfNoExceptionWasThrown(CommandFunction function, ServerCommandSource source, CallbackInfoReturnable<Integer> cir) throws ExecutionPausedThrowable {
        var globalPauseContext = FunctionDebugHandler.Companion.getCurrentPauseContext().get();
        if(globalPauseContext != null) {
            if(queue.isEmpty()) {
                globalPauseContext.onFunctionExit();
            }
            FunctionDebugHandler.Companion.getCurrentPauseContext().remove();
        }
    }

    @Inject(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiler/Profiler;pop()V",
                    ordinal = 1 //The 'finally' block when an exception was thrown
            )
    )
    private void command_crafter$resetPauseContextIfExceptionWasThrown(CommandFunction function, ServerCommandSource source, CallbackInfoReturnable<Integer> cir, @Local Throwable throwable) throws ExecutionPausedThrowable {
        var globalPauseContext = FunctionDebugHandler.Companion.getCurrentPauseContext().get();
        if(globalPauseContext != null) {
            if(!(throwable instanceof ExecutionPausedThrowable)) {
                globalPauseContext.onFunctionExit();
            }
            FunctionDebugHandler.Companion.getCurrentPauseContext().remove();
        }
    }

    @ModifyExpressionValue(
            method = "run",
            at = @At(
                    value = "CONSTANT",
                    args = "intValue=0",
                    ordinal = 0
            )
    )
    private int command_crafter$getPreviouslyRunEntryAmount(int j) {
        return command_crafter$pauseContext != null
                ? command_crafter$pauseContext.getExecutedEntries().size()
                : j;
    }

    @WrapOperation(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/function/CommandFunction;getElements()[Lnet/minecraft/server/function/CommandFunction$Element;"
            )
    )
    private CommandFunction.Element[] command_crafter$skipFunctionElementsWhenContinuingPause(CommandFunction function, Operation<CommandFunction.Element[]> op) {
        return function == null ? new CommandFunction.Element[0] : op.call(function);
    }
}
