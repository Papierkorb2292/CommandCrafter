package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.commands.arguments.SlotSourceArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SlotSourceArgument.class)
public interface SlotSourceArgumentAccessor {

    @Accessor
    SlotSourceArgument.RawArgument getHolderArgument();
}
