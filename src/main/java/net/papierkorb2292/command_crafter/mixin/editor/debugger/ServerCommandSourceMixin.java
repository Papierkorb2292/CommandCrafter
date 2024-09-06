package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
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

@Mixin(ServerCommandSource.class)
public class ServerCommandSourceMixin {

    @Inject(
            method = "sendFeedback",
            at = @At("HEAD")
    )
    private void command_crafter$logFeedbackWhenDebugging(Supplier<Text> feedbackSupplier, boolean broadcastToOps, CallbackInfo ci) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext == null || !(pauseContext.peekDebugFrame() instanceof FunctionDebugFrame debugFrame)) return;
        debugFrame.onCommandFeedback(feedbackSupplier.get().getString());
    }

    @Inject(
            method = "sendError",
            at = @At("HEAD")
    )
    private void command_crafter$logErrorWhenDebugging(Text message, CallbackInfo ci) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext == null || !(pauseContext.peekDebugFrame() instanceof FunctionDebugFrame debugFrame)) return;
        debugFrame.onCommandError(message.getString());
    }
}
