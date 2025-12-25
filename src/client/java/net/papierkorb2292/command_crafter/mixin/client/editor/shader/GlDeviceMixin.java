package net.papierkorb2292.command_crafter.mixin.client.editor.shader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlDevice;
import net.minecraft.client.renderer.ShaderManager;
import com.mojang.blaze3d.shaders.ShaderSource;
import net.minecraft.resources.Identifier;
import net.papierkorb2292.command_crafter.client.editor.DirectMinecraftClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

@Mixin(GlDevice.class)
public class GlDeviceMixin {
    @WrapMethod(method = "compilePipeline")
    private GlRenderPipeline shader_reload$retryFailedShadersWithDefault(RenderPipeline pipeline, ShaderSource sourceRetriever, Operation<GlRenderPipeline> op) {
        DirectMinecraftClientConnection.INSTANCE.setReloadingBuiltinShaders(false);
        var compiled = op.call(pipeline, sourceRetriever);
        if(compiled.isValid())
            return compiled;

        DirectMinecraftClientConnection.INSTANCE.setReloadingBuiltinShaders(true);
        var vanillyOnlyDefinitions = DirectMinecraftClientConnection.INSTANCE.getVanillaOnlyShaders();
        return op.call(
                pipeline,
                (ShaderSource)(id, type) ->
                        vanillyOnlyDefinitions.shaderSources().get(new ShaderManager.ShaderSourceKey(id, type))
        );
    }

    @WrapOperation(
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
    }
}
