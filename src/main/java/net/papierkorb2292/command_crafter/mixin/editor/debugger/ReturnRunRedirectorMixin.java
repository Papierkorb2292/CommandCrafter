package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.command.ExecutionControl;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ForkableNoPauseFlag;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

@Mixin(targets = "net.minecraft.server.command.ReturnCommand$ReturnRunRedirector")
public class ReturnRunRedirectorMixin<T extends AbstractServerCommandSource<T>> implements ForkableNoPauseFlag {
    @Override
    public boolean command_crafter$cantPause() {
        return true;
    }

    @Inject(
            method = "execute(Lnet/minecraft/server/command/AbstractServerCommandSource;Ljava/util/List;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/command/ExecutionFlags;Lnet/minecraft/command/ExecutionControl;)V",
            at = @At("HEAD")
    )
    private void command_crafter$passThroughFunctionFrameSectionSources(T abstractServerCommandSource, List<T> list, ContextChain<T> contextChain, ExecutionFlags executionFlags, ExecutionControl<T> executionControl, CallbackInfo ci) {
        if(!(abstractServerCommandSource instanceof ServerCommandSource source)) return;
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if(pauseContext == null) return;
        var debugFrame = pauseContext.peekDebugFrame();
        if(!(debugFrame instanceof FunctionDebugFrame functionDebugFrame)) return;

        //noinspection unchecked
        var commandInfo = functionDebugFrame.getCommandInfo((CommandContext<ServerCommandSource>) contextChain.getTopContext());
        if(commandInfo == null) return;

        var absoluteSectionIndex = commandInfo.getSectionOffset() + 1;
        if (functionDebugFrame.getSectionSources().size() <= absoluteSectionIndex) {
            var sectionSources = functionDebugFrame.getSectionSources();
            var sectionSource = sectionSources.get(sectionSources.size() - 1);
            sectionSource.getParentSourceIndices().addAll(IntStream.range(0, sectionSource.getSources().size()).boxed().toList());
            sectionSources.add(0, new FunctionDebugFrame.SectionSources(new ArrayList<>(sectionSource.getSources()), Collections.emptyList(), sectionSource.getSources().size()));
            functionDebugFrame.setCurrentSectionIndex(functionDebugFrame.getCurrentSectionIndex() + 1);
        }
    }
}
