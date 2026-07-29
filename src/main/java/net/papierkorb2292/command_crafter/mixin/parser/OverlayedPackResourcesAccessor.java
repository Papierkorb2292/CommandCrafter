package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.server.packs.OverlayedPackResources;
import net.minecraft.server.packs.PackMetadataResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OverlayedPackResources.class)
public interface OverlayedPackResourcesAccessor {
    @Accessor
    PackMetadataResources getPrimaryPackMetadataResources();
}
