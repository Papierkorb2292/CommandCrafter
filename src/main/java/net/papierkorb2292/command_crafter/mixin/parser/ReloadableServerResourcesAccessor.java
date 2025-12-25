package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.core.Registry;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ReloadableServerResources.class)
public interface ReloadableServerResourcesAccessor {

    @Accessor
    List<Registry.PendingTags<?>> getPostponedTags();

    @Accessor @Mutable
    void setPostponedTags(List<Registry.PendingTags<?>> pendingTagLoads);
}
