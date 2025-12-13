package net.papierkorb2292.command_crafter

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.tree.CommandNode
import com.mojang.serialization.Codec
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.advancement.Advancement
import net.minecraft.block.entity.BannerPattern
import net.minecraft.block.jukebox.JukeboxSong
import net.minecraft.block.spawner.TrialSpawnerConfig
import net.minecraft.command.CommandSource
import net.minecraft.command.permission.LeveledPermissionPredicate
import net.minecraft.dialog.type.Dialog
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.provider.EnchantmentProvider
import net.minecraft.entity.damage.DamageType
import net.minecraft.entity.decoration.painting.PaintingVariant
import net.minecraft.entity.mob.ZombieNautilusVariant
import net.minecraft.entity.passive.*
import net.minecraft.item.Instrument
import net.minecraft.item.equipment.trim.ArmorTrimMaterial
import net.minecraft.item.equipment.trim.ArmorTrimPattern
import net.minecraft.loot.LootTable
import net.minecraft.loot.condition.LootCondition
import net.minecraft.loot.function.LootFunctionTypes
import net.minecraft.network.message.MessageType
import net.minecraft.recipe.Recipe
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.tag.TagFile
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.dedicated.management.listener.BlankManagementListener
import net.minecraft.structure.StructureSet
import net.minecraft.structure.pool.StructurePool
import net.minecraft.structure.processor.StructureProcessorType
import net.minecraft.test.TestEnvironmentDefinition
import net.minecraft.test.TestInstance
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler
import net.minecraft.world.attribute.timeline.Timeline
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList
import net.minecraft.world.dimension.DimensionOptions
import net.minecraft.world.dimension.DimensionType
import net.minecraft.world.gen.FlatLevelGeneratorPreset
import net.minecraft.world.gen.WorldPreset
import net.minecraft.world.gen.carver.ConfiguredCarver
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings
import net.minecraft.world.gen.densityfunction.DensityFunction
import net.minecraft.world.gen.feature.ConfiguredFeature
import net.minecraft.world.gen.feature.PlacedFeature
import net.minecraft.world.gen.structure.JigsawStructure
import net.minecraft.world.rule.GameRule
import net.minecraft.world.rule.GameRuleCategory
import net.minecraft.world.rule.GameRuleType
import net.minecraft.world.rule.GameRuleVisitor
import net.papierkorb2292.command_crafter.config.CommandCrafterConfig
import net.papierkorb2292.command_crafter.editor.*
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler.isPlayerAllowedConnection
import net.papierkorb2292.command_crafter.editor.debugger.InitializedEventEmittingMessageWrapper
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.processing.*
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ScoreboardFileAnalyzer
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileSystemResult
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.ReadDirectoryResultEntry
import net.papierkorb2292.command_crafter.helper.UnitTypeAdapter
import net.papierkorb2292.command_crafter.mixin.parser.CommandNodeAccessor
import net.papierkorb2292.command_crafter.networking.packets.NotifyCanReloadWorldgenS2CPacket
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.apache.logging.log4j.LogManager
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.io.BufferedReader
import java.io.PrintWriter
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutorService

object CommandCrafter: ModInitializer {
    const val MOD_ID = "command_crafter"
    val LOGGER = LogManager.getLogger(MOD_ID)
    val VERSION: String = FabricLoader.getInstance().getModContainer(MOD_ID).get().metadata.version.friendlyString
    lateinit var config: CommandCrafterConfig
        private set

    private var dedicatedServerEditorConnectionManager: EditorConnectionManager? = null

    override fun onInitialize() {
        initializeConfig()
        initializeEditor()
        NetworkServerConnectionHandler.registerPacketHandlers()
        DirectServerConnection.registerReconfigureCompletedCheck()
        initializeParser()

        LOGGER.info("Loaded CommandCrafter!")
    }

    fun getBooleanSystemProperty(name: String): Boolean {
        val arg = System.getProperty(name)
        return arg != null && (arg.isEmpty() || arg.toBoolean())
    }

    private fun initializeEditor() {
        StringRangeTreeJsonResourceAnalyzer.addJsonAnalyzers(serversideJsonResourceCodecs)
        MinecraftLanguageServer.addAnalyzer(FileTypeDispatchingAnalyzer)
        MinecraftLanguageServer.addAnalyzer(PackMetaAnalyzer)
        MinecraftLanguageServer.addAnalyzer(ScoreboardFileAnalyzer)

        ServerScoreboardStorageFileSystem.registerTickUpdateRunner()
        IdArgumentTypeAnalyzer.registerFileTypeAdditionalDataType()
        DataObjectDecoding.registerDataObjectSourceAdditionalDataType()

        if(FabricLoader.getInstance().environmentType == EnvType.SERVER) {
            // When analyzing is done by a dedicated server, a ServerCommandSource can be used
            // For all other cases, the analyzer is only needed on the client side, where an AnalyzingClientCommandSource is used
            MinecraftLanguageServer.addAnalyzer(McFunctionAnalyzer({ languageServer ->
                val directServerConnection = languageServer.minecraftServer as? DirectServerConnection
                    ?: throw IllegalArgumentException("ServerConnection on dedicated server was expected to be DirectServerConnection")
                ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, directServerConnection.server.overworld, directServerConnection.functionPermissions, "", ScreenTexts.EMPTY, directServerConnection.server, null)
            }))

            if(config.runDedicatedServerServices) {
                LOGGER.warn("**** COMMANDCRAFTER SERVERSIDE SERVICES ARE ACTIVE!")
                LOGGER.warn("This lets anybody with access to the services port connect to the language server and debugger, thereby letting them control commands")
                LOGGER.warn("Only use this in a controlled environment with restricted access to the port! To deactivate, set '${CommandCrafterConfig.RUN_DEDICATED_SERVER_SERVICES_NAME}=false' in the config file and restart the server")
                ServerLifecycleEvents.SERVER_STARTED.register {
                    val editorConnectionManager = EditorConnectionManager(
                        SocketEditorConnectionType(config.servicesPort),
                        DirectServerConnection(it),
                        null,
                        serviceLaunchers
                    )
                    dedicatedServerEditorConnectionManager = editorConnectionManager
                    editorConnectionManager.startServer()
                }
            }
        }
    }

    private fun initializeParser() {
        Registry.register(LanguageManager.LANGUAGES, Identifier.of(VanillaLanguage.ID), VanillaLanguage.VanillaLanguageType)
        RawZipResourceCreator.DATA_TYPE_PROCESSORS += object : RawZipResourceCreator.DataTypeProcessor {
            override val type: String
                get() = PackContentFileType.FUNCTIONS_FILE_TYPE.contentTypePath

            override fun shouldProcess(args: DatapackBuildArgs) = !args.keepDirectives

            override fun process(
                args: DatapackBuildArgs,
                id: Identifier,
                content: BufferedReader,
                resourceCreator: RawZipResourceCreator,
                dispatcher: CommandDispatcher<CommandSource>,
            ) {
                val reader = DirectiveStringReader(FileMappingInfo(content.lines().toList()), dispatcher, resourceCreator)
                val resource = RawResource(RawResource.FUNCTION_TYPE)
                val source = ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, args.permissions ?: LeveledPermissionPredicate.GAMEMASTERS, "", ScreenTexts.EMPTY, null, null)
                LanguageManager.parseToVanilla(
                    reader,
                    source,
                    resource,
                    Language.TopLevelClosure(VanillaLanguage())
                )
                resourceCreator.addResource(id, resource)
            }

            override fun validate(
                args: DatapackBuildArgs,
                id: Identifier,
                content: BufferedReader,
                dispatcher: CommandDispatcher<CommandSource>,
            ) {
                process(args, id, content, RawZipResourceCreator(), dispatcher)
            }
        }
    }

    var shortenNbt: Boolean = true
        private set

    private fun initializeConfig() {
        val shortenNbtGameRule = Registry.register(
            Registries.GAME_RULE,
            Identifier.of("command_crafter", "shorten_nbt"),
            GameRule(
                GameRuleCategory.CHAT,
                GameRuleType.BOOL,
                BoolArgumentType.bool(),
                GameRuleVisitor::visitBoolean,
                Codec.BOOL,
                { if(it) 1 else 0 },
                true,
                FeatureSet.empty()
            )
        )
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            shortenNbt = server.saveProperties.gameRules.getValue(shortenNbtGameRule)
            server.managementListener.addListener(object : BlankManagementListener() {
                override fun <T : Any?> onGameRuleUpdated(arg: GameRule<T>, `object`: T) {
                    if(arg == shortenNbtGameRule)
                        shortenNbt = `object` as Boolean
                    else if(arg.id == DirectServerConnection.WORLDGEN_DEVTOOLS_RELOAD_REGISTRIES_ID && `object` is Boolean)
                        for(player in server.playerManager.playerList)
                            if(isPlayerAllowedConnection(player))
                                ServerPlayNetworking.send(player, NotifyCanReloadWorldgenS2CPacket(`object`))
                }
            })
        }

        config = CommandCrafterConfig.fromFile(CommandCrafterConfig.DEFAULT_CONFIG_PATH)
    }

    val serviceLaunchers = mapOf(
        "languageServer" to object : EditorConnectionManager.ServiceLauncher {
            override fun launch(
                serverConnection: MinecraftServerConnection,
                clientConnection: MinecraftClientConnection?,
                editorConnection: EditorConnection,
                executorService: ExecutorService,
                featureConfig: FeatureConfig,
            ): EditorConnectionManager.LaunchedService {
                val server = MinecraftLanguageServer(serverConnection, clientConnection, featureConfig)
                val generateLanguageServerTrace = getBooleanSystemProperty("cc_trace_language_server")
                val launcher = Launcher.Builder<CommandCrafterLanguageClient>()
                    .setLocalService(server)
                    .setRemoteInterface(CommandCrafterLanguageClient::class.java)
                    .setInput(editorConnection.inputStream)
                    .setOutput(editorConnection.outputStream)
                    .setExecutorService(executorService)
                    .setExceptionHandler {
                        handleEditorServiceException("languageServer", it)
                    }
                    .configureGson {
                        it.registerTypeAdapter(ReadDirectoryResultEntry::class.java, ReadDirectoryResultEntry.TypeAdapter)
                        it.registerTypeAdapter(Unit::class.java, UnitTypeAdapter)
                        it.registerTypeAdapterFactory(FileSystemResult.TypeAdapterFactory)
                    }
                    .traceMessages(if(generateLanguageServerTrace) PrintWriter("logs/language_server_debug_trace") else null)
                    .create();
                val launched = launcher.startListening()
                server.connect(launcher.remoteProxy)
                launcher.remoteProxy.showMessage(MessageParams(org.eclipse.lsp4j.MessageType.Info, "Connected to Minecraft"))
                return EditorConnectionManager.LaunchedService(server, EditorConnectionManager.ServiceClient(launcher.remoteProxy), launched)
            }
        },
        "debugger" to object : EditorConnectionManager.ServiceLauncher {
            override fun launch(
                serverConnection: MinecraftServerConnection,
                clientConnection: MinecraftClientConnection?,
                editorConnection: EditorConnection,
                executorService: ExecutorService,
                featureConfig: FeatureConfig,
            ): EditorConnectionManager.LaunchedService {
                val server = MinecraftDebuggerServer(serverConnection, featureConfig)
                val messageWrapper = InitializedEventEmittingMessageWrapper()
                val launcher = DebugLauncher.Builder<CommandCrafterDebugClient>()
                    .setLocalService(server)
                    .setRemoteInterface(CommandCrafterDebugClient::class.java)
                    .setInput(editorConnection.inputStream)
                    .setOutput(editorConnection.outputStream)
                    .setExecutorService(executorService)
                    .wrapMessages(messageWrapper)
                    .setExceptionHandler {
                        handleEditorServiceException("debugger", it)
                    }
                    .create();
                messageWrapper.client = launcher.remoteProxy
                val launched = launcher.startListening()
                server.connect(launcher.remoteProxy)
                return EditorConnectionManager.LaunchedService(server, EditorConnectionManager.ServiceClient(launcher.remoteProxy), launched)
            }

        }
    )

    val serversideJsonResourceCodecs = mutableMapOf(
        PackContentFileType.ADVANCEMENTS_FILE_TYPE to Advancement.CODEC,
        PackContentFileType.BANNER_PATTERN_FILE_TYPE to BannerPattern.CODEC,
        PackContentFileType.ITEM_MODIFIER_FILE_TYPE to LootFunctionTypes.CODEC,
        PackContentFileType.LOOT_TABLES_FILE_TYPE to LootTable.CODEC,
        PackContentFileType.PREDICATES_FILE_TYPE to LootCondition.CODEC,
        PackContentFileType.RECIPES_FILE_TYPE to Recipe.CODEC,
        PackContentFileType.CHAT_TYPE_FILE_TYPE to MessageType.CODEC,
        PackContentFileType.DAMAGE_TYPE_FILE_TYPE to DamageType.CODEC,
        PackContentFileType.BANNER_PATTERN_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.BLOCK_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.CAT_VARIANT_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.DAMAGE_TYPE_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.DIALOG_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.ENCHANTMENT_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.ENTITY_TYPE_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.FLUID_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.FUNCTION_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.GAME_EVENT_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.INSTRUMENT_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.ITEM_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.PAINTING_VARIANT_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.POINT_OF_INTEREST_TYPE_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.TIMELINE_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.WORLDGEN_BIOME_TYPE_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.WORLDGEN_FLAT_LEVEL_GENERATOR_PRESET_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.WORLDGEN_STRUCTURE_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.WORLDGEN_WORLD_PRESET_TAGS_FILE_TYPE to TagFile.CODEC,
        PackContentFileType.TRIM_MATERIAL_FILE_TYPE to ArmorTrimMaterial.CODEC,
        PackContentFileType.TRIM_PATTERN_FILE_TYPE to ArmorTrimPattern.CODEC,
        PackContentFileType.WOLF_VARIANT_FILE_TYPE to WolfVariant.CODEC,
        PackContentFileType.PIG_VARIANT_FILE_TYPE to PigVariant.CODEC,
        PackContentFileType.CAT_VARIANT_FILE_TYPE to CatVariant.CODEC,
        PackContentFileType.FROG_VARIANT_FILE_TYPE to FrogVariant.CODEC,
        PackContentFileType.COW_VARIANT_FILE_TYPE to CowVariant.CODEC,
        PackContentFileType.CHICKEN_VARIANT_FILE_TYPE to ChickenVariant.CODEC,
        PackContentFileType.ZOMBIE_NAUTILUS_VARIANT_FILE_TYPE to ZombieNautilusVariant.CODEC,
        PackContentFileType.WOLF_SOUND_VARIANT_FILE_TYPE to WolfSoundVariant.CODEC,
        PackContentFileType.DIMENSION_FILE_TYPE to DimensionOptions.CODEC,
        PackContentFileType.DIMENSION_TYPE_FILE_TYPE to DimensionType.CODEC,
        PackContentFileType.WORLDGEN_BIOME_FILE_TYPE to Biome.CODEC,
        PackContentFileType.WORLDGEN_CONFIGURED_CARVER_FILE_TYPE to ConfiguredCarver.CODEC,
        PackContentFileType.WORLDGEN_CONFIGURED_FEATURE_FILE_TYPE to ConfiguredFeature.CODEC,
        PackContentFileType.WORLDGEN_DENSITY_FUNCTION_FILE_TYPE to DensityFunction.CODEC,
        PackContentFileType.WORLDGEN_FLAT_LEVEL_GENERATOR_PRESET_FILE_TYPE to FlatLevelGeneratorPreset.CODEC,
        PackContentFileType.WORLDGEN_MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST_FILE_TYPE to MultiNoiseBiomeSourceParameterList.CODEC,
        PackContentFileType.WORLDGEN_NOISE_FILE_TYPE to DoublePerlinNoiseSampler.NoiseParameters.CODEC,
        PackContentFileType.WORLDGEN_NOISE_SETTINGS_FILE_TYPE to ChunkGeneratorSettings.CODEC,
        PackContentFileType.WORLDGEN_PLACED_FEATURE_FILE_TYPE to PlacedFeature.CODEC,
        PackContentFileType.WORLDGEN_PROCESSOR_LIST_FILE_TYPE to StructureProcessorType.PROCESSORS_CODEC,
        PackContentFileType.WORLDGEN_STRUCTURE_FILE_TYPE to JigsawStructure.STRUCTURE_CODEC,
        PackContentFileType.WORLDGEN_STRUCTURE_SET_FILE_TYPE to StructureSet.CODEC,
        PackContentFileType.WORLDGEN_TEMPLATE_POOL_FILE_TYPE to StructurePool.CODEC,
        PackContentFileType.WORLDGEN_WORLD_PRESET_FILE_TYPE to WorldPreset.CODEC,
        PackContentFileType.PAINTING_VARIANT_FILE_TYPE to PaintingVariant.CODEC,
        PackContentFileType.JUKEBOX_SONG_FILE_TYPE to JukeboxSong.CODEC,
        PackContentFileType.ENCHANTMENT_FILE_TYPE to Enchantment.CODEC,
        PackContentFileType.ENCHANTMENT_PROVIDER_FILE_TYPE to EnchantmentProvider.CODEC,
        PackContentFileType.DIALOG_FILE_TYPE to Dialog.CODEC,
        PackContentFileType.TIMELINE_FILE_TYPE to Timeline.CODEC,
        PackContentFileType.TRIAL_SPAWNER_FILE_TYPE to TrialSpawnerConfig.CODEC,
        PackContentFileType.INSTRUMENT_FILE_TYPE to Instrument.CODEC,
        PackContentFileType.TEST_ENVIRONMENT_FILE_TYPE to TestEnvironmentDefinition.CODEC,
        PackContentFileType.TEST_INSTANCE_FILE_TYPE to TestInstance.CODEC,
    )

    private fun handleEditorServiceException(serviceName: String, e: Throwable): ResponseError {
        var coreException = e;
        if(coreException is RuntimeException)
            coreException = coreException.cause ?: coreException
        if(coreException is InvocationTargetException)
            coreException = coreException.targetException

        if(coreException is MinecraftDebuggerServer.EvaluationFailedThrowable)
            // Errors from evaluations are normal, so don't log them as exceptions
            return ResponseError(ResponseErrorCode.RequestFailed, coreException.message, null)
        LOGGER.error("Error thrown by $serviceName", coreException)
        return ResponseError(ResponseErrorCode.UnknownErrorCode, coreException.message, null)
    }

    fun <S> removeLiteralsStartingWithForwardsSlash(node: CommandNode<S>) {
        val literals = (node as CommandNodeAccessor).literals
        val children = (node as CommandNodeAccessor).children
        val literalsIt = literals.keys.iterator()
        while(literalsIt.hasNext()) {
            val key = literalsIt.next()
            if(!key.startsWith('/')) continue
            literalsIt.remove()
            children.remove(key)
        }
    }
}