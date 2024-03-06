package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
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
public class ExecuteCommandMixin {

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
            debugFrame.checkPause(commandInfo, 0, contextChain.getTopContext(), (ServerCommandSource)source);
            original.accept(control);
        };
    }

    @SuppressWarnings("InvalidInjectorMethodSignature")
    @ModifyVariable(
            method = "enqueueExecutions",
            at = @At("STORE")
    )
    private static ReturnValueConsumer command_crafter$advanceSectionSources(ReturnValueConsumer original, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return original;

        return (success, returnValue) -> {
            var sectionSources = debugFrame.getCurrentSectionSources();
            sectionSources.setCurrentSourceIndex(sectionSources.getCurrentSourceIndex() + 1);
            original.onResult(success, returnValue);
        };
    }
}
