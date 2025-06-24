package net.papierkorb2292.command_crafter.client.editor

import net.minecraft.client.MinecraftClient
import net.minecraft.resource.LifecycledResourceManagerImpl
import net.minecraft.resource.ResourceType
import net.minecraft.resource.SimpleResourceReload
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import net.minecraft.util.profiler.DummyProfiler
import net.papierkorb2292.command_crafter.editor.MinecraftClientConnection
import net.papierkorb2292.command_crafter.editor.ReloadResourcesParams
import net.papierkorb2292.command_crafter.mixin.client.editor.shader.ShaderLoaderAccessor
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
    private var shaderReloadWaitFuture: CompletableFuture<*>? = CompletableFuture.completedFuture(Unit)
    override val isConnectedToServer: Boolean
        get() = client.world != null

    override fun reloadResources(params: ReloadResourcesParams) {
        if(params.onlyShaders != true) {
            client.inGameHud.chatHud.addMessage(
                Text.translatable("command_crafter.reload.resources").formatted(Formatting.GREEN))
            client.reloadResources()
            return
        }

        // Reload only shaders
        val shaderReloadWaitFuture = shaderReloadWaitFuture
        if(shaderReloadWaitFuture != null) {
            DirectMinecraftClientConnection.shaderReloadWaitFuture = null // No other reloads should be scheduled until this one starts
            shaderReloadWaitFuture.whenComplete { _, _ ->
                client.inGameHud.chatHud.addMessage(
                    Text.translatable("command_crafter.reload.shaders").formatted(Formatting.GREEN))
                DirectMinecraftClientConnection.shaderReloadWaitFuture = SimpleResourceReload.start(
                    client.resourceManager,
                    listOf(client.shaderLoader),
                    Util.getMainWorkerExecutor(),
                    client,
                    CompletableFuture.completedFuture(net.minecraft.util.Unit.INSTANCE),
                    false
                ).whenComplete()
            }
        }
    }
}