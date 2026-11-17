package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.commands.arguments.StringArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StringArgument.class)
public interface StringArgumentAccessor {

    @Accessor
    StringArgument.StringType getStringType();
}
