package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.packs.CompositePackResources;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CompositePackResources.class)
public interface CompositePackResourcesAccessor {
    @Accessor
    PackResources getPrimaryPackResources();
}
