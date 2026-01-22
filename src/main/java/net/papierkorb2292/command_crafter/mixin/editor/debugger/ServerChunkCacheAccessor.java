package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {
    @Invoker
    void callBroadcastChangedChunks(ProfilerFiller profilerFiller);
}
