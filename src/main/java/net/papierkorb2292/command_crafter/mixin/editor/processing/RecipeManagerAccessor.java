package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.core.HolderLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor
    HolderLookup.Provider getRegistries();
}
