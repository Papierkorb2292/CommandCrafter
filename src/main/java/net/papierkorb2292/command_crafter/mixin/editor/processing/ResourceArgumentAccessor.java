package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = {ResourceArgument.class, ResourceOrTagArgument.class})
public interface ResourceArgumentAccessor {
    @Accessor
    ResourceKey<? extends Registry<?>> getRegistryKey();
}
