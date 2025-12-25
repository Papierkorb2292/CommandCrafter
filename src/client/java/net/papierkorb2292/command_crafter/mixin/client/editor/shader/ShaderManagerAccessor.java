package net.papierkorb2292.command_crafter.mixin.client.editor.shader;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ShaderManager.class)
public interface ShaderManagerAccessor {
    @Invoker
    ShaderManager.Configs callPrepare(ResourceManager resourceManager, ProfilerFiller profiler);
}
