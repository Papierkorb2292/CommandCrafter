package net.papierkorb2292.command_crafter.client.editor

import net.minecraft.client.Minecraft
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.SimpleReloadInstance
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import net.minecraft.util.Util
import net.minecraft.util.profiling.InactiveProfiler
import net.papierkorb2292.command_crafter.editor.MinecraftClientConnection
import net.papierkorb2292.command_crafter.editor.ReloadResourcesParams
import net.papierkorb2292.command_crafter.mixin.client.editor.shader.ShaderManagerAccessor
import java.util.concurrent.CompletableFuture

object DirectMinecraftClientConnection : MinecraftClientConnection {
    private val client = Minecraft.getInstance()
    var isReloadingBuiltinShaders = false
    val vanillaOnlyShaders by lazy {
        (client.shaderManager as ShaderManagerAccessor).callPrepare(
            MultiPackResourceManager(PackType.CLIENT_RESOURCES, listOf(client.vanillaPackResources)),
            InactiveProfiler.INSTANCE
        )
    }
    private var shaderReloadWaitFuture: CompletableFuture<*>? = CompletableFuture.completedFuture(Unit)
    override val isConnectedToServer: Boolean
        get() = client.level != null

    override fun reloadResources(params: ReloadResourcesParams) {
        if(params.onlyShaders != true) {
            client.gui.chat.addMessage(
                Component.translatable("command_crafter.reload.resources").withStyle(ChatFormatting.GREEN))
            client.reloadResourcePacks()
            return
        }

        // Reload only shaders
        val shaderReloadWaitFuture = shaderReloadWaitFuture
        if(shaderReloadWaitFuture != null) {
            DirectMinecraftClientConnection.shaderReloadWaitFuture = null // No other reloads should be scheduled until this one starts
            shaderReloadWaitFuture.whenComplete{ _, _ ->
                client.execute {
                    // Has to run on render thread
                    client.gui.chat.addMessage(
                        Component.translatable("command_crafter.reload.shaders").withStyle(ChatFormatting.GREEN))
                }
                DirectMinecraftClientConnection.shaderReloadWaitFuture = SimpleReloadInstance.create(
                    client.resourceManager,
                    listOf(client.shaderManager),
                    Util.backgroundExecutor(),
                    client,
                    CompletableFuture.completedFuture(net.minecraft.util.Unit.INSTANCE),
                    false
                ).done()
            }
        }
    }
}