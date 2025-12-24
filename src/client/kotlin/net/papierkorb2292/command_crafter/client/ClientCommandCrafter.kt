package net.papierkorb2292.command_crafter.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.StringRange
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.FontManager
import net.minecraft.client.gl.PostEffectPipeline
import net.minecraft.client.item.ItemAsset
import net.minecraft.client.render.model.json.BlockModelDefinition
import net.minecraft.client.resource.waypoint.WaypointStyleAsset
import net.minecraft.client.texture.atlas.AtlasSourceManager
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.permission.LeveledPermissionPredicate
import net.minecraft.item.ItemStack
import net.minecraft.item.equipment.EquipmentType
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.world.waypoint.WaypointStyle
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.client.editor.DirectMinecraftClientConnection
import net.papierkorb2292.command_crafter.editor.*
import net.papierkorb2292.command_crafter.client.editor.processing.AnalyzingClientCommandSource
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer.Companion.emptyCompletionsDefault
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.Language
import net.papierkorb2292.command_crafter.parser.LanguageManager
import net.papierkorb2292.command_crafter.parser.helper.limitCommandTreeForSource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

object ClientCommandCrafter : ClientModInitializer {
    override fun onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register {
            // Delay initialization to ensure that the game is fully loaded when the dynamicRegistryManager is created
            initializeEditor()
        }
    }

    val defaultFeatureSet = FeatureFlags.FEATURE_MANAGER.featureSet

    var editorConnectionManager: EditorConnectionManager = EditorConnectionManager(
        SocketEditorConnectionType(CommandCrafter.config.servicesPort),
        ClientDummyServerConnection(
            CommandDispatcher(), LeveledPermissionPredicate.NONE
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
        MinecraftLanguageServer.addAnalyzer(McFunctionAnalyzer({
            AnalyzingClientCommandSource(MinecraftClient.getInstance())
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

        val registryWrapperLookup = getLoadedClientsideRegistries().combinedRegistries.combinedRegistryManager
        fun setDefaultServerConnection() {
            val rootNode = limitCommandTreeForSource(
                CommandManager(
                    CommandManager.RegistrationEnvironment.ALL,
                    CommandRegistryAccess.of(registryWrapperLookup, defaultFeatureSet)
                ), ServerCommandSource(
                    CommandOutput.DUMMY,
                    Vec3d.ZERO,
                    Vec2f.ZERO,
                    null,
                    LeveledPermissionPredicate.GAMEMASTERS,
                    "",
                    ScreenTexts.EMPTY,
                    null,
                    null
                )
            )
            CommandCrafter.removeLiteralsStartingWithForwardsSlash(rootNode)
            editorConnectionManager.minecraftServerConnection = ClientDummyServerConnection(
                CommandDispatcher(rootNode),
                LeveledPermissionPredicate.GAMEMASTERS
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
        PackContentFileType.ATLASES_FILE_TYPE to AtlasSourceManager.LIST_CODEC,
        PackContentFileType.BLOCKSTATES_FILE_TYPE to BlockModelDefinition.CODEC,
        PackContentFileType.EQUIPMENT_FILE_TYPE to EquipmentType.CODEC,
        PackContentFileType.FONTS_FILE_TYPE to FontManager.Providers.CODEC,
        PackContentFileType.ITEMS_FILE_TYPE to ItemAsset.CODEC,
        PackContentFileType.POST_EFFECTS_FILE_TYPE to PostEffectPipeline.CODEC,
        PackContentFileType.WAYPOINT_STYLE_FILE_TYPE to WaypointStyleAsset.CODEC,
    )

    var currentlyHoveredItem: ItemStack? = null
}