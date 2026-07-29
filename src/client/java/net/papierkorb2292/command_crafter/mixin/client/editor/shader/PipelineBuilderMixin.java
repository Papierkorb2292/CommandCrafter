package net.papierkorb2292.command_crafter.mixin.client.editor.shader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import net.minecraft.client.renderer.ShaderManager;
import net.papierkorb2292.command_crafter.client.editor.DirectMinecraftClientConnection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PipelineBuilder.class)
public class PipelineBuilderMixin {
    @WrapMethod(method = "compilePipeline")
    private CompiledRenderPipeline shader_reload$retryFailedShadersWithDefault(RenderPipeline pipeline, ShaderSource sourceRetriever, Operation<CompiledRenderPipeline> op) {
        var compiled = op.call(pipeline, sourceRetriever);
        if(compiled != null)
            return compiled;

        var vanillyOnlyDefinitions = DirectMinecraftClientConnection.INSTANCE.getVanillaOnlyShaders();
        return op.call(
                pipeline,
                (ShaderSource)(id, type) ->
                        vanillyOnlyDefinitions.shaderSources().get(new ShaderManager.ShaderSourceKey(id, type))
        );
    }

    /*@WrapOperation(
            method = "getOrCompileShader(Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;Lnet/minecraft/client/renderer/ShaderDefines;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/opengl/GlShaderModule;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"
            )
    )
    private <K, V> Object shader_reload$skipCacheWhenReloadingBuiltin(Map<?, ?> instance, K key, Function<? super K, ? extends V> mappingFunction, Operation<V> op) {
        if(DirectMinecraftClientConnection.INSTANCE.isReloadingBuiltinShaders())
            return mappingFunction.apply(key);
        return op.call(instance, key, mappingFunction);
    }*/
}
