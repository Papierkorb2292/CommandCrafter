package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugFrame;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(CommandSourceStack.class)
public class CommandSourceStackMixin {

    @Inject(
            method = "sendSuccess",
            at = @At("HEAD")
    )
    private void command_crafter$logFeedbackWhenDebugging(Supplier<Component> feedbackSupplier, boolean broadcastToOps, CallbackInfo ci) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext == null || !(pauseContext.peekDebugFrame() instanceof FunctionDebugFrame debugFrame)) return;
        debugFrame.onCommandFeedback(feedbackSupplier.get().getString());
    }

    @Inject(
            method = "sendFailure",
            at = @At("HEAD")
    )
    private void command_crafter$logErrorWhenDebugging(Component message, CallbackInfo ci) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext == null || !(pauseContext.peekDebugFrame() instanceof FunctionDebugFrame debugFrame)) return;
        debugFrame.onCommandError(message.getString());
    }
}
