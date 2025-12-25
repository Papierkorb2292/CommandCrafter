package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.PlainTextFunction;
import net.minecraft.commands.functions.MacroFunction;
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

@Mixin(targets = "net.minecraft.server.commands.FunctionCommand$FunctionCustomExecutor")
public abstract class FunctionCommandCommandMixin implements PotentialDebugFrameInitiator {

    @Shadow protected abstract @Nullable CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

    @Override
    public boolean command_crafter$willInitiateDebugFrame(@NotNull CommandContext<CommandSourceStack> context) {
        try {
            FunctionArgument.getFunctions(context, "name");
            return true;
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    @Override
    public boolean command_crafter$isInitializedDebugFrameEmpty(@NotNull CommandContext<CommandSourceStack> context) {
        try {
            return FunctionArgument.getFunctions(context, "name").stream().allMatch(function ->
                    function instanceof PlainTextFunction<CommandSourceStack> expandedMacro && expandedMacro.entries().isEmpty()
                            || function instanceof MacroFunction<CommandSourceStack> macro && ((MacroFunctionAccessor)macro).getEntries().isEmpty()
            );
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    @ModifyExpressionValue(
            method = "runGuarded(Lnet/minecraft/commands/CommandSourceStack;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/commands/execution/ChainModifiers;Lnet/minecraft/commands/execution/ExecutionControl;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/context/CommandContext;copyFor(Ljava/lang/Object;)Lcom/mojang/brigadier/context/CommandContext;",
                    remap = false
            )
    )
    private CommandContext<CommandSourceStack> command_crafter$pauseAtCommandAndInitiateTagDebugFrame(
            CommandContext<CommandSourceStack> functionContext,
            CommandSourceStack serverCommandSource,
            ContextChain<CommandSourceStack> contextChain,
            ChainModifiers executionFlags,
            ExecutionControl<CommandSourceStack> executionControl
    ) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        CompoundTag macros = null;
        try {
            macros = arguments(functionContext);
        } catch(CommandSyntaxException e) {
            CommandCrafter.INSTANCE.getLOGGER().debug("Error getting arguments for macro invocation when debugging /function", e);
        }
        final CompoundTag finalMacros = macros;
        executionControl.queueNext((context, frame) -> {
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
            method = "runGuarded(Lnet/minecraft/commands/CommandSourceStack;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/commands/execution/ChainModifiers;Lnet/minecraft/commands/execution/ExecutionControl;)V"
    )
    private void command_crafter$enqueueExitTagDebugFrame(CommandSourceStack serverCommandSource, ContextChain<CommandSourceStack> contextChain, ChainModifiers executionFlags, ExecutionControl<CommandSourceStack> executionControl, Operation<Void> op) {
        try {
            op.call(serverCommandSource, contextChain, executionFlags, executionControl);
        } finally {
            // This is done in 'finally' instead of injecting at return such that frames that might be created before the function throws an error are still removed.
            // If they weren't removed, the current frame wouldn't resume. The next pause could only happen after the current frame is exited.
            var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
            if(pauseContext != null) {
                executionControl.queueNext(FunctionTagDebugFrame.Companion.getLastTagPauseCommandAction());
                executionControl.queueNext(FunctionTagDebugFrame.Companion.getCOPY_TAG_RESULT_TO_COMMAND_RESULT_COMMAND_ACTION());
                executionControl.queueNext(new ExitDebugFrameCommandAction(
                        pauseContext.getDebugFrameDepth(),
                        pauseContext,
                        !executionFlags.isReturn(),
                        null
                ));
            }
        }
    }
}
