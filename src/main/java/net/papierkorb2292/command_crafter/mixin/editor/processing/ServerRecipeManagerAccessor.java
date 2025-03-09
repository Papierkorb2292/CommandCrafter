package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerRecipeManager.class)
public interface ServerRecipeManagerAccessor {
    @Accessor
    RegistryWrapper.WrapperLookup getRegistries();
}
