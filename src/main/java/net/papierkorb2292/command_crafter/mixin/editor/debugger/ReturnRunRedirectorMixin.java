package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.papierkorb2292.command_crafter.editor.debugger.helper.ForkableNoPauseFlag;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.server.command.ReturnCommand$ReturnRunRedirector")
public class ReturnRunRedirectorMixin implements ForkableNoPauseFlag {
    @Override
    public boolean command_crafter$cantPause() {
        return true;
    }
}
