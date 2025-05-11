package net.papierkorb2292.command_crafter.client

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.FontManager
import net.minecraft.client.gl.PostEffectPipeline
import net.minecraft.client.item.ItemAsset
import net.minecraft.client.texture.atlas.AtlasSourceManager
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.item.equipment.EquipmentType
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.*
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingClientCommandSource
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer.Companion.addJsonAnalyzer
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

object ClientCommandCrafter : ClientModInitializer {
    override fun onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register {
            // Delay initialization to ensure that the game is fully loaded when the dynamicRegistryManager is created
            initializeEditor()
        }
    }

    var editorConnectionManager: EditorConnectionManager = EditorConnectionManager(
        SocketEditorConnectionType(CommandCrafter.config.servicesPort),
        ClientDummyServerConnection(
            CommandDispatcher(), 0
        ),
        DirectMinecraftClientConnection,
        CommandCrafter.serviceLaunchers
    )

    private var loadedClientsideRegistries: LoadedClientsideRegistries? = null
    fun getLoadedClientsideRegistries(): LoadedClientsideRegistries {
        var loadedClientsideRegistries = loadedClientsideRegistries
        if(loadedClientsideRegistries == null) {
            loadedClientsideRegistries = LoadedClientsideRegistries.load()
            this.loadedClientsideRegistries = loadedClientsideRegistries
        }
        return loadedClientsideRegistries
    }

    private fun initializeEditor() {
        MinecraftLanguageServer.addAnalyzer(object : FileAnalyseHandler {
            override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith(".mcfunction")

            override fun analyze(
                file: OpenFile,
                languageServer: MinecraftLanguageServer,
            ): AnalyzingResult {
                val reader = DirectiveStringReader(
                    FileMappingInfo(file.stringifyLines()),
                    languageServer.minecraftServer.commandDispatcher,
                    AnalyzingResourceCreator(languageServer, file.uri)
                )
                val result = AnalyzingResult(reader.fileMappingInfo, Position())
                reader.resourceCreator.resourceStack.push(AnalyzingResourceCreator.ResourceStackEntry(result))
                val source = AnalyzingClientCommandSource(MinecraftClient.getInstance())
                LanguageManager.analyse(reader, source, result, Language.TopLevelClosure(VanillaLanguage()))
                result.clearDisabledFeatures(languageServer.featureConfig, listOf(LanguageManager.ANALYZER_CONFIG_PATH, ""))
                return result
            }
        })
        addJsonAnalyzer(PackContentFileType.ATLASES_FILE_TYPE, AtlasSourceManager.LIST_CODEC)
        addJsonAnalyzer(PackContentFileType.EQUIPMENT_FILE_TYPE, EquipmentType.CODEC)
        addJsonAnalyzer(PackContentFileType.FONTS_FILE_TYPE, FontManager.Providers.CODEC)
        addJsonAnalyzer(PackContentFileType.ITEMS_FILE_TYPE, ItemAsset.CODEC)
        addJsonAnalyzer(PackContentFileType.POST_EFFECTS_FILE_TYPE, PostEffectPipeline.CODEC)

        val registryWrapperLookup = getLoadedClientsideRegistries().combinedRegistries.combinedRegistryManager
        fun setDefaultServerConnection() {
            val rootNode = limitCommandTreeForSource(
                CommandManager(
                    CommandManager.RegistrationEnvironment.ALL,
                    CommandRegistryAccess.of(registryWrapperLookup, FeatureFlags.FEATURE_MANAGER.featureSet)
                ), ServerCommandSource(
                    CommandOutput.DUMMY,
                    Vec3d.ZERO,
                    Vec2f.ZERO,
                    null,
                    2,
                    "",
                    ScreenTexts.EMPTY,
                    null,
                    null
                )
            )
            CommandCrafter.removeLiteralsStartingWithForwardsSlash(rootNode)
            editorConnectionManager.minecraftServerConnection = ClientDummyServerConnection(
                CommandDispatcher(rootNode),
                2
            )
        }

        NetworkServerConnection.registerPacketHandlers()
        setDefaultServerConnection()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            NetworkServerConnection.requestAndCreate().thenAccept {
                editorConnectionManager.minecraftServerConnection = it
                editorConnectionManager.showMessage(MessageParams(MessageType.Info, "Successfully connected to Minecraft server"))
            }.exceptionally {
                editorConnectionManager.showMessage(MessageParams(MessageType.Warning, "Connecting to Minecraft server failed, keeping clientside connection: $it"))
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
}