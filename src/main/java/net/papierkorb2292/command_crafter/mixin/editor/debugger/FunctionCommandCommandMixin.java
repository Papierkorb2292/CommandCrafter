package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ExecutionControl;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.ExpandedMacro;
import net.minecraft.server.function.Macro;
import net.papierkorb2292.command_crafter.CommandCrafter;
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionContextContinueCallback;
import net.papierkorb2292.command_crafter.editor.debugger.helper.PotentialDebugFrameInitiator;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ExitDebugFrameCommandAction;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.server.command.FunctionCommand$Command")
public abstract class FunctionCommandCommandMixin implements PotentialDebugFrameInitiator {

    @Shadow protected abstract @Nullable NbtCompound getArguments(CommandContext<ServerCommandSource> context) throws CommandSyntaxException;

    @Override
    public boolean command_crafter$willInitiateDebugFrame(@NotNull CommandContext<ServerCommandSource> context) {
        try {
            CommandFunctionArgumentType.getFunctions(context, "name");
            return true;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    @Override
    public boolean command_crafter$isInitializedDebugFrameEmpty(@NotNull CommandContext<ServerCommandSource> context) {
        try {
            return CommandFunctionArgumentType.getFunctions(context, "name").stream().allMatch(function ->
                    function instanceof ExpandedMacro<ServerCommandSource> expandedMacro && expandedMacro.entries().isEmpty()
                            || function instanceof Macro<ServerCommandSource> macro && ((MacroAccessor)macro).getLines().isEmpty()
            );
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    @ModifyExpressionValue(
            method = "executeInner(Lnet/minecraft/server/command/ServerCommandSource;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/command/ExecutionFlags;Lnet/minecraft/command/ExecutionControl;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/context/CommandContext;copyFor(Ljava/lang/Object;)Lcom/mojang/brigadier/context/CommandContext;",
                    remap = false
            )
    )
    private CommandContext<ServerCommandSource> command_crafter$pauseAtCommandAndInitiateTagDebugFrame(
            CommandContext<ServerCommandSource> functionContext,
            ServerCommandSource serverCommandSource,
            ContextChain<ServerCommandSource> contextChain,
            ExecutionFlags executionFlags,
            ExecutionControl<ServerCommandSource> executionControl
    ) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        NbtCompound macros = null;
        try {
            macros = getArguments(functionContext);
        } catch(CommandSyntaxException e) {
            CommandCrafter.INSTANCE.getLOGGER().debug("Error getting arguments for macro invocation when debugging /function", e);
        }
        final NbtCompound finalMacros = macros;
        executionControl.enqueueAction((context, frame) -> {
            FunctionDebugFrame.Companion.checkSimpleActionPause(contextChain.getTopContext(), serverCommandSource, FunctionDebugFrame.Companion.getCommandInfo(contextChain.getTopContext()));
            if(pauseContext != null)
                FunctionTagDebugFrame.Companion.pushFrameForCommandArgumentIfIsTag(
                    functionContext,
                    "name",
                    pauseContext,
                    finalMacros,
                    new CommandExecutionContextContinueCallback(context)
                );
        });
        return functionContext;
    }

    @WrapMethod(
            method = "executeInner(Lnet/minecraft/server/command/ServerCommandSource;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/command/ExecutionFlags;Lnet/minecraft/command/ExecutionControl;)V"
    )
    private void command_crafter$enqueueExitTagDebugFrame(ServerCommandSource serverCommandSource, ContextChain<ServerCommandSource> contextChain, ExecutionFlags executionFlags, ExecutionControl<ServerCommandSource> executionControl, Operation<Void> op) {
        try {
            op.call(serverCommandSource, contextChain, executionFlags, executionControl);
        } finally {
            // This is done in 'finally' instead of injecting at return such that frames that might be created before the function throws an error are still removed.
            // If they weren't removed, the current frame wouldn't resume. The next pause could only happen after the current frame is exited.
            var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
            if(pauseContext != null) {
                executionControl.enqueueAction(FunctionTagDebugFrame.Companion.getLastTagPauseCommandAction());
                executionControl.enqueueAction(FunctionTagDebugFrame.Companion.getCOPY_TAG_RESULT_TO_COMMAND_RESULT_COMMAND_ACTION());
                executionControl.enqueueAction(new ExitDebugFrameCommandAction(
                        pauseContext.getDebugFrameDepth(),
                        FunctionDebugFrame.Companion.getCommandResult(),
                        !executionFlags.isInsideReturnRun(),
                        null
                ));
            }
        }
    }
}
