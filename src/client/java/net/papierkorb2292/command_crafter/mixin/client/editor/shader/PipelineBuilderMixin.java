package net.papierkorb2292.command_crafter.mixin.client.editor.shader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import net.papierkorb2292.command_crafter.client.editor.DirectMinecraftClientConnection;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@Mixin(PipelineBuilder.class)
public class PipelineBuilderMixin {
    @WrapMethod(method = "compilePipeline")
    private CompletableFuture<CompiledRenderPipeline.Pending> shader_reload$retryFailedShadersWithDefault(RenderPipeline pipeline, ShaderSource shaderSource, Executor executor, Operation<CompletableFuture<CompiledRenderPipeline.Pending>> op) {
        return op.call(pipeline, shaderSource, executor).thenCompose(compiled -> {
            if(compiled == CompiledRenderPipeline.Pending.NULL) {
                // The shader definitely failed to load, fallback to vanilla
                return op.call(
                        pipeline,
                        DirectMinecraftClientConnection.INSTANCE.getVanillaOnlyShaders(),
                        executor
                );
            }
            // It is not yet known whether the shader failed to load, the return value of the callback might still be null
            return CompletableFuture.completedFuture(() -> {
                var result = compiled.finishCompile();
                if(result != null)
                    return result;
                // Welp, the combined shader failed to load, fallback has to be loaded now without CompletableFuture
                try {
                    return op.call(
                            pipeline,
                            DirectMinecraftClientConnection.INSTANCE.getVanillaOnlyShaders(),
                            executor
                    ).get().finishCompile();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }
}
