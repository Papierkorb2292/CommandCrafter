package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.Pair;
import net.minecraft.server.command.FunctionCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.CommandFunctionManager;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionPausedThrowable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
@Mixin(FunctionCommand.class)
public abstract class FunctionCommandMixin {

    private static ThreadLocal<Pair<Integer, Iterator<CommandFunction>>> command_crafter$contextAfterPause = new ThreadLocal<>();

    @Shadow
    private static int execute(ServerCommandSource source, Collection<CommandFunction> functions) throws ExecutionPausedThrowable {
        throw new AssertionError();
    }

    @WrapOperation(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/function/CommandFunctionManager;execute(Lnet/minecraft/server/function/CommandFunction;Lnet/minecraft/server/command/ServerCommandSource;)I"
            )
    )
    private static int command_crafter$addCallbackOnExecutionPause(CommandFunctionManager manager, CommandFunction function, ServerCommandSource source, Operation<Integer> op, ServerCommandSource originalSource, Collection<CommandFunction> functions, @Local int i, @Local Iterator<CommandFunction> functionIterator) throws ExecutionPausedThrowable {
        try {
            return MixinUtil.<Integer, ExecutionPausedThrowable>callWithThrows(op, manager, function, source);
        } catch(ExecutionPausedThrowable paused) {
            var functionCommandCompletableFuture = new CompletableFuture<Integer>();
            paused.getFunctionCompletion().thenAccept(result -> {
                command_crafter$contextAfterPause.set(new Pair<>(i + result, functionIterator));
                try {
                    functionCommandCompletableFuture.complete(execute(originalSource, functions));
                } catch(ExecutionPausedThrowable continuePaused) {
                    continuePaused.getFunctionCompletion().whenComplete((continueResult, throwable) -> {
                        if(throwable != null)
                            functionCommandCompletableFuture.completeExceptionally(throwable);
                        else
                            functionCommandCompletableFuture.complete(continueResult);
                    });
                } catch (Throwable throwable) {
                    functionCommandCompletableFuture.completeExceptionally(throwable);
                }
            });
            throw new ExecutionPausedThrowable(functionCommandCompletableFuture);
        }
    }

    @ModifyVariable(
            method = "execute",
            at = @At("STORE")
    )
    private static int command_crafter$loadContextResultAfterPause(int original) {
        Pair<Integer, Iterator<CommandFunction>> context = command_crafter$contextAfterPause.get();
        return context != null ? context.getFirst() : original;
    }

    @ModifyExpressionValue(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;"
            )
    )
    private static Iterator<CommandFunction> command_crafter$loadContextIteratorAfterPause(Iterator<CommandFunction> original) {
        Pair<Integer, Iterator<CommandFunction>> context = command_crafter$contextAfterPause.get();
        if (context == null) return original;
        command_crafter$contextAfterPause.remove();
        return context.getSecond();
    }
}
