package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.debugger.DebugPauseHandlerFactory;
import net.papierkorb2292.command_crafter.editor.debugger.helper.ExecutionPausedThrowable;
import net.papierkorb2292.command_crafter.editor.debugger.helper.FunctionDebugPauseHandlerCreatorContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.FunctionDebugHandler;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("unused")
@Mixin(CommandFunction.CommandElement.class)
public class CommandElementMixin implements FunctionDebugPauseHandlerCreatorContainer {

    @Shadow @Final private ParseResults<ServerCommandSource> parsed;
    private @Nullable DebugPauseHandlerFactory<? super FunctionPauseContext> command_crafter$functionDebugPauseHandlerFactory;

    @Override
    public void command_crafter$setHandlerCreator(@NotNull DebugPauseHandlerFactory<? super FunctionPauseContext> creator) {
        command_crafter$functionDebugPauseHandlerFactory = creator;
    }

    @Nullable
    @Override
    public DebugPauseHandlerFactory<? super FunctionPauseContext> command_crafter$getPauseHandlerCreator() {
        return command_crafter$functionDebugPauseHandlerFactory;
    }

    @WrapOperation(
            method = "execute(Lnet/minecraft/server/function/CommandFunctionManager;Lnet/minecraft/server/command/ServerCommandSource;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/ParseResults;)I"
            )
    )
    private int command_crafter$wrapWithPauseContext(CommandDispatcher<ServerCommandSource> dispatcher, ParseResults<ServerCommandSource> parseResults, Operation<Integer> op) throws Throwable {
        var globalPauseContext = FunctionDebugHandler.Companion.getCurrentPauseContext().get();
        if(globalPauseContext == null) {
            return op.call(dispatcher, parseResults);
        }
        globalPauseContext.setCurrentCommand((CommandFunction.CommandElement)(Object)this);
        var dispatcherContext = globalPauseContext.getDispatcherContext();
        if(dispatcherContext != null) {
            FunctionDebugHandler.Companion.getCurrentDispatcherContext().set(dispatcherContext);
            globalPauseContext.setDispatcherContext(null);
        }
        int result;
        try {
            result = MixinUtil.<Integer, ExecutionPausedThrowable>callWithThrows(op, dispatcher, parseResults);
        } catch(Throwable e) {
            if (!(e instanceof ExecutionPausedThrowable)) {
                command_crafter$cleanUpExecution(globalPauseContext);
            }
            throw e;
        }
        command_crafter$cleanUpExecution(globalPauseContext);
        return result;
    }

    private void command_crafter$cleanUpExecution(FunctionPauseContextImpl pauseContext) {
        var sectionIndex = pauseContext.getCurrentSectionIndex();
        pauseContext.setCurrentSectionIndex(0);
        var contextStackCurrentSectionIndex = pauseContext.getIndexOfCurrentSectionInContextStack();
        var contextStack = pauseContext.getContextStack();
        if (contextStack.size() > 0) {
            contextStack.subList(contextStackCurrentSectionIndex - sectionIndex, contextStackCurrentSectionIndex + 1).clear();
        }
        pauseContext.setIndexOfCurrentSectionInContextStack(contextStackCurrentSectionIndex - sectionIndex - 1);
        var pauseLocation = pauseContext.getNextPauseLocation();
    }
}
