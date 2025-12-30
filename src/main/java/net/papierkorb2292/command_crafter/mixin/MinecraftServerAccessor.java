package net.papierkorb2292.command_crafter.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor
    LevelStorageSource.LevelStorageAccess getStorageSource();

    @Accessor
    MinecraftServer.ReloadableResources getResources();

    // Keeps Yarn method name for now because MultiScoreboard refers to it.
    // Could be changed when both mods have to be updated at the same time.
    @Accessor("nextTickTimeNanos")
    void setTickStartTimeNanos(long tickStartTimeNanos);
}
