package net.papierkorb2292.command_crafter.editor

import net.minecraft.client.MinecraftClient
import net.minecraft.resource.LifecycledResourceManagerImpl
import net.minecraft.resource.ResourceType
import net.minecraft.resource.SimpleResourceReload
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Unit
import net.minecraft.util.Util
import net.minecraft.util.profiler.DummyProfiler
import net.papierkorb2292.command_crafter.mixin.editor.shader.ShaderLoaderAccessor
import java.util.concurrent.CompletableFuture

object DirectMinecraftClientConnection : MinecraftClientConnection {
    private val client = MinecraftClient.getInstance()
    var isReloadingBuiltinShaders = false
    val vanillaOnlyShaders by lazy {
        (client.shaderLoader as ShaderLoaderAccessor).callPrepare(
            LifecycledResourceManagerImpl(ResourceType.CLIENT_RESOURCES, listOf(client.defaultResourcePack)),
            DummyProfiler.INSTANCE
        )
    }

    override fun reloadResources(params: ReloadResourcesParams) {
        if(params.onlyShaders != true) {
            client.inGameHud.chatHud.addMessage(Text.translatable("command_crafter.reload.resources").formatted(Formatting.GREEN))
            client.reloadResources()
            return
        }

        // Reload only shaders
        client.inGameHud.chatHud.addMessage(Text.translatable("command_crafter.reload.shaders").formatted(Formatting.GREEN))
        SimpleResourceReload.start(
            client.resourceManager,
            listOf(client.shaderLoader),
            Util.getMainWorkerExecutor(),
            client,
            CompletableFuture.completedFuture(Unit.INSTANCE),
            false
        )
    }
}