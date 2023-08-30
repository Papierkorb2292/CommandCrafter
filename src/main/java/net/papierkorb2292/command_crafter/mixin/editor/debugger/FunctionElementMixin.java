package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.CommandFunctionManager;
import net.papierkorb2292.command_crafter.editor.debugger.server.FunctionDebugHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;

@Mixin(CommandFunction.FunctionElement.class)
public class FunctionElementMixin {

    @Inject(
            method = "method_17914(Lnet/minecraft/server/function/CommandFunctionManager$Tracer;IILjava/util/Deque;Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/server/function/CommandFunction;)V",
            at = @At("HEAD"),
            remap = false
    )
    private static void command_crafter$notifyPauseContextOfFunctionEnter(CommandFunctionManager.Tracer tracer, int i, int j, Deque deque, ServerCommandSource serverCommandSource, CommandFunction f, CallbackInfo ci) {
        var globalPauseContext = FunctionDebugHandler.Companion.getCurrentPauseContext().get();
        if(globalPauseContext != null) {
            globalPauseContext.onFunctionEnter(f);
        }
    }
}
