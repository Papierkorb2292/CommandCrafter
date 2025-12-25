package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.papierkorb2292.command_crafter.editor.debugger.server.PauseContext;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.server.commands.FunctionCommand$1Accumulator")
public class ReturnValueAdderMixin {
    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void command_crafter$addToDebugFrame(CallbackInfo ci) {
        var pauseContext = getOrNull(PauseContext.Companion.getCurrentPauseContext());
        if(pauseContext == null) return;
        var debugFrame = pauseContext.peekDebugFrame();
        if(!(debugFrame instanceof FunctionTagDebugFrame tagFrame)) return;
        tagFrame.setReturnValueAdder((ReturnValueAdderAccessor)this);
    }
}
