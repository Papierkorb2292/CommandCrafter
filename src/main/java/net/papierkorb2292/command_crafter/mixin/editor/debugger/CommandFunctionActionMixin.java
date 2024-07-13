package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import kotlin.Pair;
import kotlin.Unit;
import net.minecraft.command.*;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.Procedure;
import net.papierkorb2292.command_crafter.CommandCrafter;
import net.papierkorb2292.command_crafter.editor.PackagedId;
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionContextContinueCallback;
import net.papierkorb2292.command_crafter.editor.debugger.helper.DebugInformationContainer;
import net.papierkorb2292.command_crafter.editor.debugger.helper.MacroValuesContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.CommandResult;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.ExitDebugFrameCommandAction;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import net.papierkorb2292.command_crafter.parser.helper.FileSourceContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;

@Mixin(CommandFunctionAction.class)
public class CommandFunctionActionMixin<T extends AbstractServerCommandSource<T>> {

    @Shadow @Final private Procedure<T> function;

    @Shadow @Final private boolean propagateReturn;

    @Inject(
            method = "execute(Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/command/CommandExecutionContext;Lnet/minecraft/command/Frame;)V",
            at = @At("RETURN")
    )
    private void command_crafter$createFunctionDebugFrame(T abstractServerCommandSource, CommandExecutionContext<T> commandExecutionContext, Frame frame, CallbackInfo ci) {
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if (pauseContext == null || !(function instanceof DebugInformationContainer<?,?> container))
            return;
        //noinspection unchecked
        var debugInformation = ((DebugInformationContainer<?, FunctionDebugFrame>) container).command_crafter$getDebugInformation();
        if (debugInformation == null)
            return;
        if (!(function instanceof FileSourceContainer fileSource)) {
            CommandCrafter.INSTANCE.getLOGGER().warn("Function {} does not implement FileSourceContainer, no debug frame will be created", function);
            return;
        }
        var lines = fileSource.command_crafter$getFileSourceLines();
        var fileId = fileSource.command_crafter$getFileSourceId();
        if(lines == null || fileId == null) {
            CommandCrafter.INSTANCE.getLOGGER().warn("Function {} does not have a valid file source, no debug frame will be created", function);
            return;
        }

        MacroValuesContainer macroValuesContainer = function instanceof MacroValuesContainer macroValuesContainer2 ? macroValuesContainer2 : null;
        var macroNames = macroValuesContainer != null ? macroValuesContainer.command_crafter$getMacroNames() : null;
        var macroValues = macroValuesContainer != null ? macroValuesContainer.command_crafter$getMacroValues() : null;

        //noinspection unchecked
        var debugFrame = new FunctionDebugFrame(
                pauseContext,
                (Procedure<ServerCommandSource>) function,
                debugInformation,
                macroNames != null ? macroNames : List.of(),
                macroValues != null ? macroValues : List.of(),
                new CommandExecutionContextContinueCallback(commandExecutionContext),
                fileId,
                lines
        );
        //noinspection unchecked
        commandExecutionContext.enqueueCommand((CommandQueueEntry<T>) new CommandQueueEntry<>(frame,
                new ExitDebugFrameCommandAction(
                        pauseContext.getDebugFrameDepth(),
                        FunctionDebugFrame.Companion.getCommandResult(),
                        true, //TODO: Should be !propagateReturn once tags are implemented as well
                        null
                )));
        pauseContext.pushDebugFrame(debugFrame);
    }

    @ModifyExpressionValue(
            method = "execute(Lnet/minecraft/server/command/AbstractServerCommandSource;Lnet/minecraft/command/CommandExecutionContext;Lnet/minecraft/command/Frame;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/command/CommandFunctionAction;returnValueConsumer:Lnet/minecraft/command/ReturnValueConsumer;"
            )
    )
    private ReturnValueConsumer command_crafter$saveReturnValue(ReturnValueConsumer returnValueConsumer, T abstractServerCommandSource, CommandExecutionContext<T> commandExecutionContext, Frame frame) {
        var pauseContext = PauseContext.Companion.getCurrentPauseContext().get();
        if (pauseContext == null)
            return returnValueConsumer;
        var currentFrameDepth =  pauseContext.getDebugFrameDepth();
        return new ReturnValueConsumer() {
            @Override
            public void onResult(boolean successful, int returnValue) {
                setCommandResult(successful, returnValue);
                if (!pauseContext.isDebugging()) {
                    returnValueConsumer.onResult(successful, returnValue);
                    return;
                }
                enqueueFrameExitWithReturnValue();
            }

            @Override
            public void onSuccess(int successful) {
                setCommandResult(true, successful);
                if (!pauseContext.isDebugging()) {
                    returnValueConsumer.onSuccess(successful);
                    return;
                }
                enqueueFrameExitWithReturnValue();
            }

            @Override
            public void onFailure() {
                setCommandResult(false, 0);
                if (!pauseContext.isDebugging()) {
                    returnValueConsumer.onFailure();
                    return;
                }
                enqueueFrameExitWithReturnValue();
            }

            private void setCommandResult(boolean successful, int returnValue) {
                FunctionDebugFrame.Companion.getCommandResult().set(new CommandResult(new Pair<>(successful, returnValue)));
            }

            private void enqueueFrameExitWithReturnValue() {
                var exitDebugFrameCommandAction = new ExitDebugFrameCommandAction(
                        currentFrameDepth,
                        null,
                        false,
                        () -> {
                            var commandResult = FunctionDebugFrame.Companion.getCommandResult().get();
                            if(commandResult == null) return Unit.INSTANCE;
                            var returnValue = commandResult.getReturnValue();
                            if(returnValue != null)
                                returnValueConsumer.onResult(returnValue.getFirst(), returnValue.getSecond());
                            return Unit.INSTANCE;
                        }
                );
                //noinspection unchecked
                commandExecutionContext.enqueueCommand((CommandQueueEntry<T>) new CommandQueueEntry<>(frame, exitDebugFrameCommandAction));
            }
        };
    }
}
