package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.commands.functions.FunctionBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(FunctionBuilder.class)
public interface FunctionBuilderAccessor {

    @Accessor
    List<Object> getMacroEntries();
}
