package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.CommandFunctionManager;
import net.minecraft.world.timer.FunctionTagTimerCallback;
import net.minecraft.world.timer.Timer;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionPausedThrowable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collections;
import java.util.Iterator;

@SuppressWarnings("unused")
@Mixin(FunctionTagTimerCallback.class)
public abstract class FunctionTagTimerCallbackMixin {

    @Shadow
    public abstract void call(MinecraftServer minecraftServer, Timer<MinecraftServer> timer, long l);

    private static ThreadLocal<Iterator<CommandFunction>> command_crafter$contextAfterPause = new ThreadLocal<>();

    @WrapOperation(
            method = "call(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/timer/Timer;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/function/CommandFunctionManager;execute(Lnet/minecraft/server/function/CommandFunction;Lnet/minecraft/server/command/ServerCommandSource;)I"
            )
    )
    private int command_crafter$catchExecutionPauseThrowable(CommandFunctionManager manager, CommandFunction function, ServerCommandSource source, Operation<Integer> op, MinecraftServer server, Timer<MinecraftServer> timer, long tick, @Local LocalRef<Iterator<CommandFunction>> functionIterator) {
        try {
            return MixinUtil.<Integer, ExecutionPausedThrowable>callWithThrows(op, manager, function, source);
        } catch (ExecutionPausedThrowable paused) {
            var functions = functionIterator.get();
            paused.getFunctionCompletion().thenAccept(result -> {
                command_crafter$contextAfterPause.set(functions);
                call(server, timer, tick);
            });
            functionIterator.set(Collections.emptyIterator());
            return 0;
        }
    }

    @ModifyExpressionValue(
            method = "call(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/timer/Timer;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;"
            )
    )
    private Iterator<CommandFunction> command_crafter$loadIteratorAfterPause(Iterator<CommandFunction> original) {
        var afterPause = command_crafter$contextAfterPause.get();
        if (afterPause == null) return original;
        command_crafter$contextAfterPause.set(null);
        return afterPause;
    }
}
