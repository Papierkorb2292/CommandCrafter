package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.command.MacroInvocation;
import net.minecraft.server.function.Macro;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Macro.VariableLine.class)
public interface VariableLineAccessor {

    @Accessor
    MacroInvocation getInvocation();
}
