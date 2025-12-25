package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.commands.arguments.NbtPathArgument$MatchElementNode")
public interface NbtPathArgumentTypeFilteredListElementNodeAccessor {
    @Accessor
    CompoundTag getPattern();
}
