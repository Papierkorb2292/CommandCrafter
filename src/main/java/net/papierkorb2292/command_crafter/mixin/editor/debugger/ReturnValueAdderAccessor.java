package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net/minecraft/server/command/FunctionCommand$ReturnValueAdder")
public interface ReturnValueAdderAccessor {
    @Accessor
    void setReturnValue(int returnValue);
    @Accessor
    void setSuccessful(boolean successful);
}
