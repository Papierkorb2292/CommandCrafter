package net.papierkorb2292.command_crafter.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.StringRange
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.font.FontManager
import net.minecraft.client.renderer.PostChainConfig
import net.minecraft.client.renderer.block.model.BlockModelDefinition
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.client.renderer.texture.atlas.SpriteSources
import net.minecraft.client.resources.WaypointStyle
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.Commands
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.ArmorType
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.client.editor.DirectMinecraftClientConnection
import net.papierkorb2292.command_crafter.client.editor.processing.AnalyzingClientCommandSource
import net.papierkorb2292.command_crafter.editor.EditorConnectionManager
import net.papierkorb2292.command_crafter.editor.McFunctionAnalyzer
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.SocketEditorConnectionType
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.parser.helper.limitCommandTreeForSource
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

object ClientCommandCrafter : ClientModInitializer {
    override fun onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register {
            // Delay initialization to ensure that the game is fully loaded when the dynamicRegistryManager is created
            initializeEditor()
        }
    }

    val defaultFeatureSet = FeatureFlags.REGISTRY.allFlags()

    var editorConnectionManager: EditorConnectionManager = EditorConnectionManager(
        SocketEditorConnectionType(CommandCrafter.config.servicesPort),
        ClientDummyServerConnection(
            CommandDispatcher(), LevelBasedPermissionSet.NO_PERMISSIONS
        ),
        DirectMinecraftClientConnection,
        CommandCrafter.serviceLaunchers
    )

    private var loadedClientsideRegistries: LoadedClientsideRegistries? = null
    fun getLoadedClientsideRegistries(): LoadedClientsideRegistries {
        var loadedClientsideRegistries = loadedClientsideRegistries
        if(loadedClientsideRegistries == null) {
            loadedClientsideRegistries = LoadedClientsideRegistries.load()
            ClientCommandCrafter.loadedClientsideRegistries = loadedClientsideRegistries
        }
        return loadedClientsideRegistries
    }

    private fun initializeEditor() {
        CommandCrafter.registerDynamicRegistries()
        CommandCrafter.registerRegistryTags()

        MinecraftLanguageServer.addAnalyzer(McFunctionAnalyzer({ languageServer ->
            AnalyzingClientCommandSource(Minecraft.getInstance(), languageServer)
        }, { analyzingResult ->
            val finalResult = analyzingResult.copyExceptCompletions()
            finalResult.addCompletionProvider(
                AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL,
                AnalyzingResult.RangedDataProvider(StringRange(0, finalResult.mappingInfo.accumulatedLineLengths.last())) { cursor ->
                    AnalyzingClientCommandSource.allowServersideCompletions.set(true)
                    analyzingResult.getCompletionProviderForCursor(cursor)?.dataProvider(cursor) ?: CompletableFuture.completedFuture(listOf())
                },
                false
            )
            finalResult
        }))

        StringRangeTreeJsonResourceAnalyzer.addJsonAnalyzers(clientsideJsonResourceCodecs)

        val registryWrapperLookup = getLoadedClientsideRegistries().combinedRegistries.compositeAccess()
        fun setDefaultServerConnection() {
            val rootNode = limitCommandTreeForSource(
                Commands(
                    Commands.CommandSelection.ALL,
                    CommandBuildContext.simple(registryWrapperLookup, defaultFeatureSet)
                ), Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER)
            )
            CommandCrafter.removeLiteralsStartingWithForwardsSlash(rootNode)
            editorConnectionManager.minecraftServerConnection = ClientDummyServerConnection(
                CommandDispatcher(rootNode),
                LevelBasedPermissionSet.GAMEMASTER
            )
        }

        NetworkServerConnection.registerPacketHandlers()
        setDefaultServerConnection()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            NetworkServerConnection.requestAndCreate().thenAccept {
                editorConnectionManager.minecraftServerConnection = it
                editorConnectionManager.showMessage(MessageParams(MessageType.Info, "Successfully connected to Minecraft server"))
            }.exceptionally { exception ->
                val coreException = if(exception is CompletionException) exception.cause!! else exception
                val message = "Connecting to Minecraft server failed, keeping clientside connection: ${coreException.message}"
                CommandCrafter.LOGGER.warn(message)
                editorConnectionManager.showMessage(MessageParams(MessageType.Warning, message))
                null
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            setDefaultServerConnection()
            // Remove tags that were received from the server and apply the tags known to the client
            loadedClientsideRegistries?.applyTags()
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            editorConnectionManager.leave()
        }

        editorConnectionManager.startServer()

        CommandCrafter.config.addServicesPortChangedListener {
            editorConnectionManager.stopServer()
            editorConnectionManager = editorConnectionManager.copyForNewConnectionAcceptor(SocketEditorConnectionType(it))
            editorConnectionManager.startServer()
        }
    }

    val clientsideJsonResourceCodecs = mutableMapOf(
        PackContentFileType.ATLASES_FILE_TYPE to SpriteSources.FILE_CODEC,
        PackContentFileType.BLOCKSTATES_FILE_TYPE to BlockModelDefinition.CODEC,
        PackContentFileType.EQUIPMENT_FILE_TYPE to ArmorType.CODEC,
        PackContentFileType.FONTS_FILE_TYPE to FontManager.FontDefinitionFile.CODEC,
        PackContentFileType.ITEMS_FILE_TYPE to ClientItem.CODEC,
        PackContentFileType.POST_EFFECTS_FILE_TYPE to PostChainConfig.CODEC,
        PackContentFileType.WAYPOINT_STYLE_FILE_TYPE to WaypointStyle.CODEC,
    )

    var currentlyHoveredItem: ItemStack? = null
}