package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.commands.FunctionCommand$1Accumulator")
public interface ReturnValueAdderAccessor {
    @Accessor
    void setSum(int returnValue);
    @Accessor
    void setAnyResult(boolean successful);
}
