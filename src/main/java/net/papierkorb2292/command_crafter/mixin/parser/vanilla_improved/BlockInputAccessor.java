package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockInput.class)
public interface BlockInputAccessor {
    @Accessor
    CompoundTag getTag();
}
