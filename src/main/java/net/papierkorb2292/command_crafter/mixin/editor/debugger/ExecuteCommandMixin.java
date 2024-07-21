package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandAction;
import net.minecraft.command.ExecutionControl;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.command.ReturnValueConsumer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ExecuteCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
            method = "enqueueExecutions",
            at = @At("HEAD")
    )
    private static <T extends AbstractServerCommandSource<T>> void command_crafter$initCommandInfo(T baseSource, List<T> sources, Function<T, T> functionSourceGetter, IntPredicate predicate, ContextChain<T> contextChain, @Nullable NbtCompound args, ExecutionControl<T> control, ExecuteCommand.FunctionNamesGetter<T, Collection<CommandFunction<T>>> functionNamesGetter, ExecutionFlags flags, CallbackInfo ci, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfo, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        if (!(baseSource instanceof ServerCommandSource)) return;
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if(pauseContext == null) return;
        var debugFrame = pauseContext.peekDebugFrame();
        if(!(debugFrame instanceof FunctionDebugFrame functionDebugFrame)) return;
        //noinspection unchecked
        commandInfo.set(functionDebugFrame.getCommandInfo((CommandContext<ServerCommandSource>)contextChain.getTopContext()));
        debugFrameRef.set(functionDebugFrame);
    }

    @ModifyVariable(
            method = "enqueueExecutions",
            at = @At("STORE"),
            ordinal = 1
    )
    private static <T extends AbstractServerCommandSource<T>> List<T> command_crafter$createNextSectionSources(List<T> sources, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfo, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return sources;
        var sectionIndex = 1 + commandInfo.get().getSectionOffset();
        if(debugFrame.getSectionSources().size() > sectionIndex) {
            //noinspection unchecked
            return (List<T>) debugFrame.getSectionSources().get(sectionIndex).getSources();
        }
        //noinspection unchecked
        debugFrame.getSectionSources().add(new FunctionDebugFrame.SectionSources((List<ServerCommandSource>) sources, new ArrayList<>(), 0));
        return sources;
    }

    @ModifyArg(
            method = "enqueueExecutions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/IsolatedCommandAction;<init>(Ljava/util/function/Consumer;Lnet/minecraft/command/ReturnValueConsumer;)V"
            )
    )
    private static <T extends AbstractServerCommandSource<T>> Consumer<ExecutionControl<T>> command_crafter$wrapIsolatedToCheckFunctionPause(
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
            debugFrame.checkPause(commandInfo, contextChain.getTopContext(), (ServerCommandSource)source);
            var sectionSources = debugFrame.getCurrentSectionSources();
            sectionSources.setCurrentSourceIndex(sectionSources.getCurrentSourceIndex() + 1);
            original.accept(control);
        };
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(
            method = "enqueueExecutions",
            at = @At("STORE")
    )
    private static <T extends AbstractServerCommandSource<T>> ReturnValueConsumer command_crafter$setParentSourceIndex(ReturnValueConsumer original, T baseSource, List<T> sources, Function<T, T> functionSourceGetter, IntPredicate predicate, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
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
            method = "enqueueExecutions",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lnet/minecraft/command/ExecutionControl;enqueueAction(Lnet/minecraft/command/CommandAction;)V"
            )
    )
    private static <T extends AbstractServerCommandSource<T>> CommandAction<T> command_crafter$advanceCurrentSectionIndex(CommandAction<T> original, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef) {
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
                    target = "Lnet/minecraft/command/ExecutionControl;enqueueAction(Lnet/minecraft/command/CommandAction;)V"
            )
    )
    private static <T extends AbstractServerCommandSource<T>> CommandAction<T> command_crafter$addTagPauseCheck(CommandAction<T> original, @Share("entryIndex") LocalIntRef entryIndexRef) {
        var wrappedAction = FunctionTagDebugFrame.Companion.wrapCommandActionWithTagPauseCheck(original, entryIndexRef.get());
        entryIndexRef.set(entryIndexRef.get() + 1);
        return wrappedAction;
    }
}
