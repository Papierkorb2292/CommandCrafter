package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.server.commands.InCommandFunction;
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionContextContinueCallback;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ExitDebugFrameCommandAction;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import net.papierkorb2292.command_crafter.mixin.editor.CommandsAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;

@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandMixin {

    @Inject(
            method = "scheduleFunctionConditionsAndTest",
            at = @At("HEAD")
    )
    private static <T extends ExecutionCommandSource<T>> void command_crafter$initCommandInfo(T baseSource, List<T> sources, Function<T, T> functionSourceGetter, IntPredicate predicate, ContextChain<T> contextChain, @Nullable CompoundTag args, ExecutionControl<T> control, InCommandFunction<CommandContext<T>, Collection<CommandFunction<T>>> functionNamesGetter, ChainModifiers flags, CallbackInfo ci, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfo, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        if (!(baseSource instanceof CommandSourceStack)) return;
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if(pauseContext == null) return;
        var debugFrame = pauseContext.peekDebugFrame();
        if(!(debugFrame instanceof FunctionDebugFrame functionDebugFrame)) return;
        //noinspection unchecked
        commandInfo.set(functionDebugFrame.getCommandInfo((CommandContext<CommandSourceStack>)contextChain.getTopContext()));
        debugFrameRef.set(functionDebugFrame);
    }

    @ModifyVariable(
            method = "scheduleFunctionConditionsAndTest",
            at = @At("STORE"),
            ordinal = 1
    )
    private static <T extends ExecutionCommandSource<T>> List<T> command_crafter$createNextSectionSources(List<T> sources, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfo, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return sources;
        var sectionIndex = 1 + commandInfo.get().getSectionOffset();
        if(debugFrame.getSectionSources().size() > sectionIndex) {
            //noinspection unchecked
            return (List<T>) debugFrame.getSectionSources().get(sectionIndex).getSources();
        }
        //noinspection unchecked
        debugFrame.getSectionSources().add(new FunctionDebugFrame.SectionSources((List<CommandSourceStack>) sources, new ArrayList<>(), 0));
        return sources;
    }

    @ModifyArg(
            method = "scheduleFunctionConditionsAndTest",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/tasks/IsolatedCall;<init>(Ljava/util/function/Consumer;Lnet/minecraft/commands/CommandResultCallback;)V"
            )
    )
    private static <T extends ExecutionCommandSource<T>> Consumer<ExecutionControl<T>> command_crafter$wrapIsolatedToCheckFunctionPause(
            Consumer<ExecutionControl<T>> original,
            @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef,
            @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef,
            @Local(argsOnly = true) ContextChain<T> contextChain,
            @Local(ordinal = 2) T source
    ) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return original;
        var commandInfo = commandInfoRef.get();
        return control -> {
            debugFrame.setCurrentSectionIndex(commandInfo.getSectionOffset());
            debugFrame.checkPause(commandInfo, contextChain.getTopContext(), (CommandSourceStack)source);
            var sectionSources = debugFrame.getCurrentSectionSources();
            sectionSources.setCurrentSourceIndex(sectionSources.getCurrentSourceIndex() + 1);

            //noinspection unchecked
            var isTag = FunctionTagDebugFrame.Companion.pushFrameForCommandArgumentIfIsTag((
                    CommandContext<CommandSourceStack>) contextChain.getTopContext().copyFor(source),
                    "name",
                    debugFrame.getPauseContext(),
                    null,
                    new CommandExecutionContextContinueCallback(CommandsAccessor.getCURRENT_EXECUTION_CONTEXT().get())
            );

            original.accept(control);

            if(isTag) {
                //noinspection unchecked
                control.queueNext((EntryAction<T>) FunctionTagDebugFrame.Companion.getLastTagPauseCommandAction());
                //noinspection unchecked
                control.queueNext((EntryAction<T>) FunctionTagDebugFrame.Companion.getCOPY_TAG_RESULT_TO_COMMAND_RESULT_COMMAND_ACTION());
                //noinspection unchecked
                control.queueNext((EntryAction<T>) new ExitDebugFrameCommandAction(
                        debugFrame.getPauseContext().getDebugFrameDepth() - 1,
                        debugFrame.getPauseContext(),
                        true,
                        null
                ));
            }
        };
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(
            method = "scheduleFunctionConditionsAndTest",
            at = @At("STORE")
    )
    private static <T extends ExecutionCommandSource<T>> CommandResultCallback command_crafter$setParentSourceIndex(CommandResultCallback original, T baseSource, List<T> sources, Function<T, T> functionSourceGetter, IntPredicate predicate, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return original;

        return (success, returnValue) -> {
            if(predicate.test(returnValue)) {
                var sectionSources = debugFrame.getCurrentSectionSources();
                var currentSourceIndex = sectionSources.getCurrentSourceIndex() - 1;
                debugFrame.getSectionSources().get(debugFrame.getCurrentSectionIndex() + 1).getParentSourceIndices().add(currentSourceIndex);
            }
            original.onResult(success, returnValue);
        };
    }

    @ModifyArg(
            method = "scheduleFunctionConditionsAndTest",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lnet/minecraft/commands/execution/ExecutionControl;queueNext(Lnet/minecraft/commands/execution/EntryAction;)V"
            )
    )
    private static <T extends ExecutionCommandSource<T>> EntryAction<T> command_crafter$advanceCurrentSectionIndex(EntryAction<T> original, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return original;
        return (context, frame) -> {
            debugFrame.setCurrentSectionIndex(commandInfoRef.get().getSectionOffset() + 1);
            original.execute(context, frame);
        };
    }

    @ModifyArg(
            method = "method_54852",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/execution/ExecutionControl;queueNext(Lnet/minecraft/commands/execution/EntryAction;)V",
                    ordinal = 0
            )
    )
    private static <T extends ExecutionCommandSource<T>> EntryAction<T> command_crafter$addTagPauseCheck(EntryAction<T> original, @Share("entryIndex") LocalIntRef entryIndexRef) {
        var wrappedAction = FunctionTagDebugFrame.Companion.wrapCommandActionWithTagPauseCheck(original, entryIndexRef.get());
        entryIndexRef.set(entryIndexRef.get() + 1);
        return wrappedAction;
    }
}
