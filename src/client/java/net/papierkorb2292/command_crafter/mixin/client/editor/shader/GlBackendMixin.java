package net.papierkorb2292.command_crafter.mixin.client.editor.shader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.client.gl.CompiledShaderPipeline;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.ShaderSourceGetter;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.client.editor.DirectMinecraftClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

@Mixin(GlBackend.class)
public class GlBackendMixin {
    @WrapMethod(method = "compileRenderPipeline")
    private CompiledShaderPipeline shader_reload$retryFailedShadersWithDefault(RenderPipeline pipeline, ShaderSourceGetter sourceRetriever, Operation<CompiledShaderPipeline> op) {
        DirectMinecraftClientConnection.INSTANCE.setReloadingBuiltinShaders(false);
        var compiled = op.call(pipeline, sourceRetriever);
        if(compiled.isValid())
            return compiled;

        DirectMinecraftClientConnection.INSTANCE.setReloadingBuiltinShaders(true);
        var vanillyOnlyDefinitions = DirectMinecraftClientConnection.INSTANCE.getVanillaOnlyShaders();
        return op.call(
                pipeline,
                (ShaderSourceGetter)(id, type) ->
                        vanillyOnlyDefinitions.shaderSources().get(new ShaderLoader.ShaderSourceKey(id, type))
        );
    }

    @WrapOperation(
            method = "compileShader(Lnet/minecraft/util/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;Lnet/minecraft/client/gl/Defines;Lnet/minecraft/client/gl/ShaderSourceGetter;)Lnet/minecraft/client/gl/CompiledShader;",
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
