package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.commands.functions.StringTemplate;
import net.minecraft.commands.functions.MacroFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MacroFunction.MacroEntry.class)
public interface VariableLineAccessor {

    @Accessor
    StringTemplate getTemplate();
}
