package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.ReloadableResources.class)
public interface ResourceManagerHolderAccessor {

    @Accessor
    ReloadableServerResources getManagers();
}
