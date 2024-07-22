package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.command.ExecutionControl;
import net.minecraft.command.ExecutionFlags;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.papierkorb2292.command_crafter.editor.debugger.helper.CommandExecutionContextContinueCallback;
import net.papierkorb2292.command_crafter.editor.debugger.helper.PotentialDebugFrameInitiator;
import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import net.papierkorb2292.command_crafter.mixin.editor.CommandManagerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.server.command.ExecuteCommand$IfUnlessRedirector")
public class ExecuteIfUnlessRedirectorMixin implements PotentialDebugFrameInitiator {
    @Override
    public boolean command_crafter$willInitiateDebugFrame() {
        return true;
    }

    @Inject(
            method = "method_54269",
            at = @At("HEAD")
    )
    private static void command_crafter$initiateTagDebugFrame(CommandContext<ServerCommandSource> context, CallbackInfoReturnable<Collection<CommandFunction<ServerCommandSource>>> cir) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext != null)
            FunctionTagDebugFrame.Companion.pushFrameForCommandArgumentIfIsTag(context,
                    "name",
                    pauseContext,
                    null,
                    new CommandExecutionContextContinueCallback(CommandManagerAccessor.getCURRENT_CONTEXT().get())
            );
    }
}
