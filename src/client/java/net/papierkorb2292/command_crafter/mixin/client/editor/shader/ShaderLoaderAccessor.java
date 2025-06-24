package net.papierkorb2292.command_crafter.mixin.client.editor.shader;

import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ShaderLoader.class)
public interface ShaderLoaderAccessor {
    @Invoker
    ShaderLoader.Definitions callPrepare(ResourceManager resourceManager, Profiler profiler);
}
