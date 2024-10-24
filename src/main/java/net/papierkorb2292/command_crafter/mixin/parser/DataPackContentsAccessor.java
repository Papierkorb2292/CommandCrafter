package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.registry.Registry;
import net.minecraft.server.DataPackContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(DataPackContents.class)
public interface DataPackContentsAccessor {

    @Accessor
    List<Registry.PendingTagLoad<?>> getPendingTagLoads();

    @Accessor @Mutable
    void setPendingTagLoads(List<Registry.PendingTagLoad<?>> pendingTagLoads);
}
