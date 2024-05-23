package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.command.ExecutionControl;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"net.minecraft.server.command.ReturnCommand$ValueCommand", "net.minecraft.server.command.ReturnCommand$FailCommand"})
public class ReturnValueFailCommandMixin {

    @Inject(
            method = "execute(Ljava/lang/Object;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/command/ExecutionFlags;Lnet/minecraft/command/ExecutionControl;)V",
            at = @At("HEAD")
    )
    private void command_crafter$checkPause(Object abstractServerCommandSource, ContextChain<?> contextChain, ExecutionFlags flags, ExecutionControl<?> control, CallbackInfo ci) {
        if(!(abstractServerCommandSource instanceof ServerCommandSource source)) return;
        //noinspection unchecked
        FunctionDebugFrame.Companion.checkSimpleActionPause((CommandContext<ServerCommandSource>) contextChain.getTopContext(), source, null);
    }
}
