package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net/minecraft/command/argument/NbtPathArgumentType$FilteredListElementNode")
public interface NbtPathArgumentTypeFilteredListElementNodeAccessor {
    @Accessor
    NbtCompound getFilter();
}
