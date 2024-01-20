package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kotlin.sequences.SequencesKt;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionPausedThrowable;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ServerDebugManagerContainer;
import net.papierkorb2292.command_crafter.editor.debugger.helper.UtilKt;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugHandler;
import net.papierkorb2292.command_crafter.editor.debugger.server.breakpoints.ServerBreakpoint;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.DispatcherContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionBreakpointLocation;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionPauseContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
@Mixin(CommandDispatcher.class)
public abstract class CommandDispatcherMixin<S> {

    @Shadow(remap = false) public abstract int execute(ParseResults<S> parse) throws CommandSyntaxException;

    @Shadow(remap = false) private ResultConsumer<S> consumer;

    @Inject(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At("HEAD"),
            remap = false
    )
    private void command_crafter$getBreakpoints(ParseResults<S> parse, CallbackInfoReturnable<Integer> cir, @Share("breakpoints") LocalRef<List<ServerBreakpoint<FunctionBreakpointLocation>>> breakpoints) {
        var source = parse.getContext().getSource();
        if(source instanceof ServerCommandSource serverCommandSource) {
            var debugManager = ((ServerDebugManagerContainer)serverCommandSource.getServer()).command_crafter$getServerDebugManager();
            var functionDebugHandler = debugManager.getFunctionDebugHandler();
            //noinspection unchecked
            breakpoints.set(SequencesKt.toList(functionDebugHandler.getBreakpoints((ParseResults<ServerCommandSource>) parse)));
        }
    }

    @WrapOperation(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/context/CommandContextBuilder;build(Ljava/lang/String;)Lcom/mojang/brigadier/context/CommandContext;"
            ),
            remap = false
    )
    private CommandContext<S> command_crafter$loadPreviousCommandContext(CommandContextBuilder<S> builder, String input, Operation<CommandContext<S>> op) {
        if(builder.getSource() instanceof ServerCommandSource) {
            var additionalDispatcherContext = FunctionDebugHandler.Companion.getCurrentDispatcherContext().get();
            if (additionalDispatcherContext != null) {
                //noinspection unchecked
                return (CommandContext<S>) additionalDispatcherContext.getOriginal();
            }
        }
        return op.call(builder, input);
    }

    @ModifyVariable(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            ),
            remap = false
    )
    private ArrayList<CommandContext<S>> command_crafter$loadPreviousDispatcherContext(
            ArrayList<CommandContext<S>> originalNext,
            @Local(ordinal = 0) LocalIntRef result,
            @Local(ordinal = 1) LocalIntRef successfulForks,
            @Local(ordinal = 0) LocalBooleanRef forked,
            @Local(ordinal = 1) LocalBooleanRef foundCommand,
            @Local CommandContext<S> original,
            @Local LocalRef<List<CommandContext<S>>> contexts,
            @Share("startContextIndex") LocalRef<Integer> startContextIndex,
            @Share("branchContextGroupEndIndices") LocalRef<List<Integer>> branchContextGroupEndIndicesRef
    ) {
        if(original.getSource() instanceof ServerCommandSource) {
            var globalPauseContext = FunctionDebugHandler.Companion.getCurrentPauseContext().get();
            var additionalDispatcherContext = FunctionDebugHandler.Companion.getCurrentDispatcherContext().get();
            FunctionDebugHandler.Companion.getCurrentDispatcherContext().remove();
            if (additionalDispatcherContext != null) {
                if(globalPauseContext != null) {
                    var prevSectionIndex = globalPauseContext.getCurrentSectionIndex();
                    var currentSectionIndex = additionalDispatcherContext.getCurrentSectionIndex();
                    globalPauseContext.setCurrentSectionIndex(currentSectionIndex);
                    globalPauseContext.setIndexOfCurrentSectionInContextStack(globalPauseContext.getIndexOfCurrentSectionInContextStack() - prevSectionIndex + currentSectionIndex);
                }
                startContextIndex.set(additionalDispatcherContext.getCurrentContextIndex());
                result.set(additionalDispatcherContext.getResult());
                successfulForks.set(additionalDispatcherContext.getSuccessfulForks());
                forked.set(additionalDispatcherContext.getForked());
                foundCommand.set(additionalDispatcherContext.getFoundCommand());
                //noinspection unchecked
                contexts.set((List<CommandContext<S>>) (Object) additionalDispatcherContext.getContexts());
                branchContextGroupEndIndicesRef.set(additionalDispatcherContext.getBranchContextGroupEndIndices());
                //noinspection unchecked
                return (ArrayList<CommandContext<S>>) (Object) additionalDispatcherContext.getNext();
            }
            if (globalPauseContext != null) {
                globalPauseContext.setCurrentSectionIndex(0);
                globalPauseContext.setIndexOfCurrentSectionInContextStack(globalPauseContext.getContextStack().size());
                //noinspection unchecked
                globalPauseContext.getContextStack().add(new FunctionPauseContext.SectionContexts((List<CommandContext<ServerCommandSource>>) (Object) contexts.get(), Collections.singletonList(0), 0));
            }
        }
        return originalNext;
    }

    @ModifyExpressionValue(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "CONSTANT",
                    args = "intValue=0",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Ljava/util/List;size()I"
                    )
            ),
            remap = false
    )
    private int command_crafter$loadPreviousContextIndex(int original, @Share("startContextIndex") LocalRef<Integer> startContextIndex) {
        return Objects.requireNonNullElse(startContextIndex.get(), original);
    }

    @ModifyReceiver(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/context/CommandContext;getChild()Lcom/mojang/brigadier/context/CommandContext;"
            ),
            remap = false
    )
    private CommandContext<S> command_crafter$pauseIfBreakpointHitOrPauseLocationReached(
            CommandContext<S> context,
            ParseResults<S> parse,
            @Share("breakpoints") LocalRef<List<ServerBreakpoint<FunctionBreakpointLocation>>> breakpointsRef,
            @Local(ordinal = 0) int result,
            @Local(ordinal = 1) int successfulForks,
            @Local(ordinal = 3) int i,
            @Local(ordinal = 0) boolean forked,
            @Local(ordinal = 1) boolean foundCommand,
            @Local(ordinal = 0) CommandContext<S> original,
            @Local List<CommandContext<S>> contexts,
            @Local ArrayList<CommandContext<S>> next,
            @Share("branchContextGroupEndIndices") LocalRef<List<Integer>> branchContextGroupEndIndicesRef,
            @Share("pauseAfterContextExecution") LocalBooleanRef pauseAfterContextExecutionRef
    ) throws ExecutionPausedThrowable {
        var pauseContext = FunctionDebugHandler.Companion.getCurrentPauseContext().get();
        if(pauseContext == null) return context;

        var nextPauseLocation = pauseContext.getNextPauseLocation();
        if(nextPauseLocation != null && nextPauseLocation.getCommand().getContext() == parse.getContext() && nextPauseLocation.getSectionIndex() == pauseContext.getCurrentSectionIndex()) {
            if(nextPauseLocation.getPauseAfterExecution()) {
                pauseAfterContextExecutionRef.set(true);
            } else {
                //noinspection unchecked
                pauseContext.onPauseLocationReached(new DispatcherContext(result, successfulForks, forked, foundCommand, (CommandContext<ServerCommandSource>) original, (List<CommandContext<ServerCommandSource>>)(Object)contexts, (ArrayList<CommandContext<ServerCommandSource>>)(Object)next, pauseContext.getCurrentSectionIndex(), branchContextGroupEndIndicesRef.get(), i));
            }
            return context;
        }
        if (pauseContext.getCurrentEditorConnection() == null) {
            var breakpoints = breakpointsRef.get();
            if (breakpoints == null) return context;
            for (var breakpoint : breakpoints) {
                var action = breakpoint.getAction();
                if (action == null || breakpoint.getDebuggerConnection().isPaused()) continue;
                if (action.getLocation().getCommandSectionLocation() != UtilKt.get(parse.getContext(), pauseContext.getCurrentSectionIndex()))
                    continue;
                if (breakpoint == pauseContext.getBreakpointToSkip()) {
                    continue;
                }
                var condition = action.getCondition();
                if (condition != null) {
                    //noinspection unchecked
                    var castedContext = (CommandContext<ServerCommandSource>) context;
                    if (!(condition.checkCondition(castedContext) && condition.checkHitCondition(castedContext)))
                        continue;
                }
                var branchContextGroupEndIndices = branchContextGroupEndIndicesRef.get();
                pauseContext.setBreakpointToSkip(breakpoint);
                //noinspection unchecked
                pauseContext.onBreakpointHit(breakpoint, new DispatcherContext(result, successfulForks, forked, foundCommand, (CommandContext<ServerCommandSource>) original, (List<CommandContext<ServerCommandSource>>) (Object) contexts, (ArrayList<CommandContext<ServerCommandSource>>) (Object) next, pauseContext.getCurrentSectionIndex(), branchContextGroupEndIndices != null ? branchContextGroupEndIndices : Collections.nCopies(i, -1), i));
                break;
            }
        }
        pauseContext.setBreakpointToSkip(null);
        return context;

    }

    @ModifyVariable(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "STORE",
                    ordinal = 1
            ),
            remap = false
    )
    private List<CommandContext<S>> command_crafter$updatePauseContext(
            List<CommandContext<S>> contexts,
            ParseResults<S> parseResults,
            @Local(ordinal = 0) int result,
            @Local(ordinal = 1) int successfulForks,
            @Local(ordinal = 0) boolean forked,
            @Local(ordinal = 1) boolean foundCommand,
            @Local(ordinal = 0) CommandContext<S> original,
            @Local List<CommandContext<S>> prevContexts,
            @Local ArrayList<CommandContext<S>> next,
            @Share("branchContextGroupEndIndices") LocalRef<List<Integer>> branchContextGroupEndIndicesRef
    ) {
        var pauseContext = FunctionDebugHandler.Companion.getCurrentPauseContext().get();
        if(pauseContext != null && contexts != null) {
            var sectionIndex = pauseContext.getCurrentSectionIndex() + 1;
            pauseContext.setCurrentSectionIndex(sectionIndex);
            pauseContext.setIndexOfCurrentSectionInContextStack(pauseContext.getContextStack().size());
            var branchContextGroupEndIndices = branchContextGroupEndIndicesRef.get();
            //noinspection unchecked
            pauseContext.getContextStack().add(new FunctionPauseContext.SectionContexts((List<CommandContext<ServerCommandSource>>)(Object)contexts, branchContextGroupEndIndices != null ? branchContextGroupEndIndices : Collections.nCopies(prevContexts.size(), -1), 0));
            branchContextGroupEndIndicesRef.set(null);
            var nextPauseLocation = pauseContext.getNextPauseLocation();
            if(nextPauseLocation != null && nextPauseLocation.getCommand() == parseResults && nextPauseLocation.getSectionIndex() == pauseContext.getCurrentSectionIndex()) {
                //noinspection unchecked
                pauseContext.skippedPauseLocation(new DispatcherContext(result, successfulForks, forked, foundCommand, (CommandContext<ServerCommandSource>) original, (List<CommandContext<ServerCommandSource>>)(Object)contexts, null, sectionIndex, null, 0));
                return contexts;
            }
        }
        return contexts;
    }

    @Inject(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/ArrayList;<init>(I)V"
            ),
            remap = false
    )
    private void command_crafter$prepareBranchContextGroupEndIndices(ParseResults<S> parse, CallbackInfoReturnable<Integer> cir, @Local(ordinal = 3) int contextIndex, @Share("branchContextGroupEndIndices") LocalRef<List<Integer>> branchContextGroupEndIndices) {
        branchContextGroupEndIndices.set(new ArrayList<>(Collections.nCopies(contextIndex, -1)));
    }

    @ModifyVariable(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            ordinal = 3,
            at = @At(
                    value = "LOAD",
                    ordinal = 0
            ),
            remap = false
    )
    private int command_crafter$updateBranchContextGroupEndIndices(
            int contextIndex,
            ParseResults<S> parseResults,
            @Local(ordinal = 0) int result,
            @Local(ordinal = 1) int successfulForks,
            @Local(ordinal = 0) boolean forked,
            @Local(ordinal = 1) boolean foundCommand,
            @Local(ordinal = 0) CommandContext<S> original,
            @Local List<CommandContext<S>> prevContexts,
            @Local ArrayList<CommandContext<S>> next,
            @Share("branchContextGroupEndIndices") LocalRef<List<Integer>> branchContextGroupEndIndicesRef,
            @Share("pauseAfterContextExecution") LocalBooleanRef pauseAfterContextExecutionRef
    ) throws ExecutionPausedThrowable {
        var branchContextGroupEndIndices = branchContextGroupEndIndicesRef.get();
        if(branchContextGroupEndIndices != null && branchContextGroupEndIndices.size() < contextIndex) {
            branchContextGroupEndIndices.add(next.size() - 1);
        }
        var pauseContext = FunctionDebugHandler.Companion.getCurrentPauseContext().get();
        if(pauseContext != null && pauseAfterContextExecutionRef.get()) {
            //noinspection unchecked
            pauseContext.onPauseLocationReached(new DispatcherContext(result, successfulForks, forked, foundCommand, (CommandContext<ServerCommandSource>) original, (List<CommandContext<ServerCommandSource>>) (Object) prevContexts, (ArrayList<CommandContext<ServerCommandSource>>) (Object) next, pauseContext.getCurrentSectionIndex(), branchContextGroupEndIndices != null ? branchContextGroupEndIndices : Collections.nCopies(contextIndex + 1, -1), contextIndex + 1));
        }
        return contextIndex;
    }

    @ModifyExpressionValue(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collections;singletonList(Ljava/lang/Object;)Ljava/util/List;"
            ),
            remap = false
    )
    private List<CommandContext<S>> command_crafter$makeStartContextListMutable(List<CommandContext<S>> contexts) {
        return new ArrayList<>(contexts);
    }

    @WrapOperation(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/Command;run(Lcom/mojang/brigadier/context/CommandContext;)I"
            ),
            remap = false
    )
    private int command_crafter$catchExecutionPauseThrowable(
            Command<S> command,
            CommandContext<S> context,
            Operation<Integer> op,
            ParseResults<S> parse,
            @Local(ordinal = 0) int result,
            @Local(ordinal = 1) int successfulForks,
            @Local(ordinal = 3) int i,
            @Local(ordinal = 0) boolean forked,
            @Local(ordinal = 1) boolean foundCommand,
            @Local(ordinal = 0) CommandContext<S> original,
            @Local List<CommandContext<S>> contexts,
            @Local ArrayList<CommandContext<S>> next,
            @Share("shouldCancel") LocalBooleanRef shouldCancel,
            @Share("branchContextGroupEndIndices") LocalRef<List<Integer>> branchContextGroupEndIndicesRef
    ) throws ExecutionPausedThrowable {
        try {
            return MixinUtil.<Integer, ExecutionPausedThrowable>callWithThrows(op, command, context);
        } catch(ExecutionPausedThrowable paused) {
            if(FunctionDebugHandler.Companion.getCurrentPauseContext().get() != null)
                throw paused; // The command is executed in a function
            var branchContextGroupEndIndices = branchContextGroupEndIndicesRef.get();
            paused.getFunctionCompletion().thenAccept(continueResult -> {
                consumer.onCommandComplete(context, true, continueResult);
                //noinspection unchecked
                FunctionDebugHandler.Companion.getCurrentDispatcherContext().set(
                        new DispatcherContext(
                                result + continueResult,
                                successfulForks + 1,
                                forked,
                                foundCommand,
                                (CommandContext<ServerCommandSource>)original,
                                (List<CommandContext<ServerCommandSource>>)(Object)contexts,
                                (ArrayList<CommandContext<ServerCommandSource>>)(Object)next,
                                0, // Doesn't matter
                                branchContextGroupEndIndices != null ? branchContextGroupEndIndices : Collections.singletonList(0),
                                i + 1
                        )
                );
                try {
                    execute(parse);
                } catch (CommandSyntaxException ignored) { }
            });
            shouldCancel.set(true);
            return 0;
        }
    }

    @Inject(
            method = "execute(Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/Command;run(Lcom/mojang/brigadier/context/CommandContext;)I",
                    shift = At.Shift.AFTER
            ),
            cancellable = true,
            remap = false
    )
    private void command_crafter$cancelIfPaused(ParseResults<S> parse, CallbackInfoReturnable<Integer> cir, @Share("shouldCancel") LocalBooleanRef shouldCancel) {
        if (shouldCancel.get()) {
            cir.setReturnValue(0);
        }
    }
}
