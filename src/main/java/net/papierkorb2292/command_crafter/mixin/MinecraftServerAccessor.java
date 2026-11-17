package net.papierkorb2292.command_crafter.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor
    MinecraftServer.ReloadableResources getResources();

    @Accessor
    void setNextTickTimeNanos(long tickStartTimeNanos);
}
