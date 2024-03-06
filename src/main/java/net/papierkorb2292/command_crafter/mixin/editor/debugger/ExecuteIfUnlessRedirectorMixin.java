package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.papierkorb2292.command_crafter.editor.debugger.helper.PotentialDebugFrameInitiator;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.server.command.ExecuteCommand$IfUnlessRedirector")
public class ExecuteIfUnlessRedirectorMixin implements PotentialDebugFrameInitiator {
    @Override
    public boolean command_crafter$willInitiateDebugFrame() {
        return true;
    }
}
