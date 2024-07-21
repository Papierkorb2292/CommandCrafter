package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.command.ExecutionControl;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.FunctionCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionContextContinueCallback;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FunctionCallDebugInfo;
import net.papierkorb2292.command_crafter.editor.debugger.helper.PotentialDebugFrameInitiator;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import net.papierkorb2292.command_crafter.mixin.editor.CommandManagerAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.server.command.FunctionCommand$Command")
public class FunctionCommandCommandMixin implements PotentialDebugFrameInitiator {

    @Override
    public boolean command_crafter$willInitiateDebugFrame() {
        return true;
    }

    @Inject(
            method = "executeInner(Lnet/minecraft/server/command/ServerCommandSource;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/command/ExecutionFlags;Lnet/minecraft/command/ExecutionControl;)V",
            at = @At("HEAD")
    )
    private void command_crafter$initiateTagDebugFrame(ServerCommandSource serverCommandSource, ContextChain<ServerCommandSource> contextChain, ExecutionFlags executionFlags, ExecutionControl<ServerCommandSource> executionControl, CallbackInfo ci) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext != null)
            FunctionTagDebugFrame.Companion.pushFrameForCommandArgumentIfIsTag(contextChain.getTopContext(),
                    "name",
                    pauseContext,
                    new CommandExecutionContextContinueCallback(CommandManagerAccessor.getCURRENT_CONTEXT().get())
            );
    }

    @WrapOperation(
            method = "executeInner(Lnet/minecraft/server/command/ServerCommandSource;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/command/ExecutionFlags;Lnet/minecraft/command/ExecutionControl;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/command/FunctionCommand;enqueueAction(Ljava/util/Collection;Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/command/ExecutionControl;Lnet/minecraft/server/command/FunctionCommand$ResultConsumer;Lnet/minecraft/command/ExecutionFlags;)V"
            )
    )
    private <T extends AbstractServerCommandSource<T>> void command_crafter$setFunctionCallDebugInfo(Collection<CommandFunction<T>> collection, @Nullable NbtCompound nbtCompound, T serverCommandSource, T serverCommandSource2, ExecutionControl<T> executionControl, FunctionCommand.ResultConsumer<T> resultConsumer, ExecutionFlags executionFlags, Operation<Void> op, @Local(argsOnly = true) ContextChain<ServerCommandSource> chain, @Local(argsOnly = true) ServerCommandSource source) {
        var commandInfo = FunctionDebugFrame.Companion.getCommandInfo(chain.getTopContext());
        if (commandInfo != null)
            FunctionDebugFrame.Companion.getFunctionCallDebugInfo().set(new FunctionCallDebugInfo(chain.getTopContext(), source, commandInfo));
        try {
            //noinspection MixinExtrasOperationParameters
            op.call(collection, nbtCompound, serverCommandSource, serverCommandSource2, executionControl, resultConsumer, executionFlags);
        } finally {
            FunctionDebugFrame.Companion.getFunctionCallDebugInfo().remove();
        }
    }
}
