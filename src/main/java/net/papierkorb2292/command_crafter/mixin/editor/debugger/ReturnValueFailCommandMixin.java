package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.CommandSourceStack;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"net.minecraft.server.commands.ReturnCommand$ReturnValueCustomExecutor", "net.minecraft.server.commands.ReturnCommand$ReturnFailCustomExecutor"})
public class ReturnValueFailCommandMixin {

    @Inject(
            method = "run(Ljava/lang/Object;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/commands/execution/ChainModifiers;Lnet/minecraft/commands/execution/ExecutionControl;)V",
            at = @At("HEAD")
    )
    private <T> void command_crafter$checkPause(T executionCommandSource, ContextChain<T> contextChain, ChainModifiers chainModifiers, ExecutionControl<T> executionControl, CallbackInfo ci) {
        if(!(executionCommandSource instanceof CommandSourceStack source)) return;
        //noinspection unchecked
        FunctionDebugFrame.Companion.checkSimpleActionPause((CommandContext<CommandSourceStack>) contextChain.getTopContext(), source, null);
    }
}
