package net.papierkorb2292.command_crafter.mixin.client.editor.shader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.PipelineBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPassLayout;
import net.papierkorb2292.command_crafter.client.editor.DirectMinecraftClientConnection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PipelineBuilder.class)
public class PipelineBuilderMixin {
    @WrapMethod(method = "compilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/pipeline/RenderPassLayout$Subpass;Lcom/mojang/blaze3d/pipeline/ShaderSource;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline$Pending;")
    private CompiledRenderPipeline.Pending shader_reload$retryFailedShadersWithDefault(RenderPipeline pipeline, RenderPassLayout.Subpass subpass, ShaderSource shaderSource, Operation<CompiledRenderPipeline.Pending> op) {
        var compiled = op.call(pipeline, subpass, shaderSource);
        if(compiled == CompiledRenderPipeline.Pending.NULL) {
            // The shader definitely failed to load, fallback to vanilla
            return op.call(pipeline, subpass, DirectMinecraftClientConnection.INSTANCE.getVanillaOnlyShaders());
        }
        // It is not yet known whether the shader failed to load, the return value of the callback might still be null
        return () -> {
            var result = compiled.finishCompile();
            if(result != null)
                return result;
            // Welp, the combined shader failed to load, fallback has to be loaded now without an executor
            return op.call(pipeline, subpass, DirectMinecraftClientConnection.INSTANCE.getVanillaOnlyShaders()).finishCompile();
        };
    }
}
