package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.command.CommandExecutionContext;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.command.Frame;
import net.minecraft.command.SingleCommandAction;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugPauseHandlerCreatorIndexConsumer;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Mixin(SingleCommandAction.class)
public class SingleCommandActionMixin<T extends AbstractServerCommandSource<T>> implements DebugPauseHandlerCreatorIndexConsumer {

    @Shadow @Final private ContextChain<T> contextChain;

    @Override
    public void command_crafter$setPauseHandlerCreatorIndex(int index) {
        ((DebugPauseHandlerCreatorIndexConsumer)contextChain).command_crafter$setPauseHandlerCreatorIndex(index);
    }

    @Inject(
            method = "execute",
            at = @At("HEAD")
    )
    private void command_crafter$initCommandInfo(T baseSource, List<T> sources, CommandExecutionContext<T> context, Frame frame, ExecutionFlags flags, CallbackInfo ci, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        if (!(baseSource instanceof ServerCommandSource)) return;
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if(pauseContext == null) return;
        var debugFrame = pauseContext.peekDebugFrame();
        if(!(debugFrame instanceof FunctionDebugFrame functionDebugFrame)) return;
        //noinspection unchecked
        commandInfoRef.set(functionDebugFrame.getCommandInfo((CommandContext<ServerCommandSource>)contextChain.getTopContext()));
        debugFrameRef.set(functionDebugFrame);
        functionDebugFrame.setCurrentCommandIndex(commandInfoRef.get().getCommandIndex());
    }

    @ModifyExpressionValue(
            method = "execute",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/command/SingleCommandAction;contextChain:Lcom/mojang/brigadier/context/ContextChain;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private ContextChain<T> command_crafter$advanceContextChainForContinue(ContextChain<T> chain, @Share("debugFrame") LocalRef<FunctionDebugFrame> frameRef, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef, @Share("sectionIndex") LocalIntRef sectionIndexRef) {
        var frame = frameRef.get();
        if (frame != null) {
            for (var i = commandInfoRef.get().getSectionOffset(); i < frame.getCurrentSectionIndex(); i++) {
                chain = chain.nextStage();
            }
            sectionIndexRef.set(Math.max(frame.getCurrentSectionIndex(), commandInfoRef.get().getSectionOffset()));
        }
        return chain;
    }

    @Inject(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/context/ContextChain;nextStage()Lcom/mojang/brigadier/context/ContextChain;",
                    remap = false
            )
    )
    private void command_crafter$advanceCommandSection(T baseSource, List<T> sources, CommandExecutionContext<T> context, Frame frame, ExecutionFlags flags, CallbackInfo ci, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if (debugFrame != null) {
            debugFrame.setCurrentSectionIndex(debugFrame.getCurrentSectionIndex() + 1);
        }
    }

    @ModifyVariable(
            method = "execute",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            ),
            ordinal = 1
    )
    private List<T> command_crafter$createFirstSectionSources(List<T> sources, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return sources;
        var absoluteSectionIndex = debugFrame.getCurrentSectionIndex();
        if(debugFrame.getSectionSources().size() > absoluteSectionIndex) {
            //noinspection unchecked
            return (List<T>) debugFrame.getSectionSources().get(absoluteSectionIndex).getSources();
        }
        //Make mutable for debugger variable references
        sources = new ArrayList<>(sources);
        //noinspection unchecked
        debugFrame.getSectionSources().add(new FunctionDebugFrame.SectionSources((List<ServerCommandSource>) sources, new ArrayList<>(), 0));
        return sources;
    }

    @ModifyVariable(
            method = "execute",
            at = @At("STORE"),
            ordinal = 2
    )
    private List<T> command_crafter$createNextSectionSources(List<T> sources, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return sources;
        var absoluteSectionIndex = debugFrame.getCurrentSectionIndex() + 1;
        if(debugFrame.getSectionSources().size() > absoluteSectionIndex) {
            //noinspection unchecked
            return (List<T>) debugFrame.getSectionSources().get(absoluteSectionIndex).getSources();
        }
        //noinspection unchecked
        debugFrame.getSectionSources().add(new FunctionDebugFrame.SectionSources((List<ServerCommandSource>) sources, new ArrayList<>(), 0));
        return sources;
    }

    @ModifyArg(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z"
            )
    )
    private Collection<T> command_crafter$setParentSourceIndices(Collection<T> newSources, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return newSources;
        var sectionSources = debugFrame.getSectionSources().get(debugFrame.getCurrentSectionIndex() + 1);
        sectionSources.getParentSourceIndices().addAll(Collections.nCopies(newSources.size(), debugFrame.getCurrentSectionSources().getCurrentSourceIndex() - 1));
        return newSources;
    }

    @ModifyExpressionValue(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/context/CommandContext;getRedirectModifier()Lcom/mojang/brigadier/RedirectModifier;",
                    remap = false
            )
    )
    private RedirectModifier<T> command_crafter$createNextForwardedSectionSources(RedirectModifier<T> modifier, @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        if (modifier != null) return modifier;
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return null;
        var absoluteSectionIndex = debugFrame.getCurrentSectionIndex() + 1;
        if (debugFrame.getSectionSources().size() <= absoluteSectionIndex) {
            var sectionSources = debugFrame.getSectionSources();
            sectionSources.add(sectionSources.get(sectionSources.size() - 1));
        }
        return null;
    }

    @ModifyExpressionValue(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Iterator;next()Ljava/lang/Object;",
                    ordinal = 0
            )
    )
    private Object command_crafter$checkIteratingModifierPause(
            Object source,
            @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef,
            @Share("commandInfo") LocalRef<FunctionDebugFrame.CommandInfo> commandInfoRef,
            @Local CommandContext<ServerCommandSource> topContext
            ) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return source;
        debugFrame.checkPause(commandInfoRef.get(), topContext, (ServerCommandSource) source);
        var sectionSources = debugFrame.getCurrentSectionSources();
        sectionSources.setCurrentSourceIndex(sectionSources.getCurrentSourceIndex() + 1);
        return source;
    }

    @ModifyReceiver(
            method = "execute",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;iterator()Ljava/util/Iterator;",
                    ordinal = 0
            )
    )
    private List<T> command_crafter$skipProcessedSources(List<T> sources, @Share("debugFrame") LocalRef<FunctionDebugFrame> debugFrameRef) {
        var debugFrame = debugFrameRef.get();
        if(debugFrame == null) return sources;
        return sources.subList(debugFrame.getCurrentSectionSources().getCurrentSourceIndex(), sources.size());
    }
}
