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
import net.minecraft.advancements.Advancement
import net.minecraft.world.level.block.entity.BannerPattern
import net.minecraft.world.item.JukeboxSong
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerConfig
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.dialog.Dialog
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.decoration.painting.PaintingVariant
import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant
import net.minecraft.world.item.Instrument
import net.minecraft.world.item.equipment.trim.TrimMaterial
import net.minecraft.world.item.equipment.trim.TrimPattern
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions
import net.minecraft.network.chat.ChatType
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.minecraft.tags.TagFile
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.network.chat.CommonComponents
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.notifications.EmptyNotificationService
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType
import net.minecraft.gametest.framework.TestEnvironmentDefinition
import net.minecraft.gametest.framework.GameTestInstance
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.animal.chicken.ChickenVariant
import net.minecraft.world.entity.animal.cow.CowVariant
import net.minecraft.world.entity.animal.feline.CatVariant
import net.minecraft.world.entity.animal.frog.FrogVariant
import net.minecraft.world.entity.animal.pig.PigVariant
import net.minecraft.world.entity.animal.wolf.WolfSoundVariant
import net.minecraft.world.entity.animal.wolf.WolfVariant
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.levelgen.synth.NormalNoise
import net.minecraft.world.timeline.Timeline
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset
import net.minecraft.world.level.levelgen.presets.WorldPreset
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings
import net.minecraft.world.level.levelgen.DensityFunction
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRuleCategory
import net.minecraft.world.level.gamerules.GameRuleType
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor
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
import org.eclipse.lsp4j.jsonrpc.debug.adapters.DebugEnumTypeAdapter
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
                CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, directServerConnection.server.overworld(), directServerConnection.functionPermissions, "", CommonComponents.EMPTY, directServerConnection.server, null)
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
        Registry.register(LanguageManager.LANGUAGES, Identifier.parse(VanillaLanguage.ID), VanillaLanguage.VanillaLanguageType)
        RawZipResourceCreator.DATA_TYPE_PROCESSORS += object : RawZipResourceCreator.DataTypeProcessor {
            override val type: String
                get() = PackContentFileType.FUNCTIONS_FILE_TYPE.contentTypePath

            override fun shouldProcess(args: DatapackBuildArgs) = !args.keepDirectives

            override fun process(
                args: DatapackBuildArgs,
                id: Identifier,
                content: BufferedReader,
                resourceCreator: RawZipResourceCreator,
                dispatcher: CommandDispatcher<SharedSuggestionProvider>,
            ) {
                val reader = DirectiveStringReader(FileMappingInfo(content.lines().toList()), dispatcher, resourceCreator)
                val resource = RawResource(RawResource.FUNCTION_TYPE)
                val source = Commands.createCompilationContext(args.permissions ?: LevelBasedPermissionSet.GAMEMASTER)
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
                dispatcher: CommandDispatcher<SharedSuggestionProvider>,
            ) {
                process(args, id, content, RawZipResourceCreator(), dispatcher)
            }
        }
    }

    var shortenNbt: Boolean = true
        private set

    private fun initializeConfig() {
        val shortenNbtGameRule = Registry.register(
            BuiltInRegistries.GAME_RULE,
            Identifier.fromNamespaceAndPath("command_crafter", "shorten_nbt"),
            GameRule(
                GameRuleCategory.CHAT,
                GameRuleType.BOOL,
                BoolArgumentType.bool(),
                GameRuleTypeVisitor::visitBoolean,
                Codec.BOOL,
                { if(it) 1 else 0 },
                true,
                FeatureFlagSet.of()
            )
        )
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            shortenNbt = server.worldData.gameRules.get(shortenNbtGameRule)
            server.notificationManager().registerService(object : EmptyNotificationService() {
                override fun <T : Any> onGameRuleChanged(arg: GameRule<T>, `object`: T) {
                    if(arg == shortenNbtGameRule)
                        shortenNbt = `object` as Boolean
                    else if(arg.identifier == DirectServerConnection.WORLDGEN_DEVTOOLS_RELOAD_REGISTRIES_ID && `object` is Boolean)
                        for(player in server.playerList.players)
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
                editorInfo: EditorConnectionManager.EditorInfo,
            ): EditorConnectionManager.LaunchedService {
                val server = MinecraftLanguageServer(serverConnection, clientConnection, editorInfo)
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
                        it.registerTypeAdapter(FeatureConfig.Entry::class.java, DebugEnumTypeAdapter(FeatureConfig.Entry::class.java))
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
                editorInfo: EditorConnectionManager.EditorInfo,
            ): EditorConnectionManager.LaunchedService {
                val server = MinecraftDebuggerServer(serverConnection, editorInfo)
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
        PackContentFileType.BANNER_PATTERN_FILE_TYPE to BannerPattern.DIRECT_CODEC,
        PackContentFileType.ITEM_MODIFIER_FILE_TYPE to LootItemFunctions.ROOT_CODEC,
        PackContentFileType.LOOT_TABLES_FILE_TYPE to LootTable.DIRECT_CODEC,
        PackContentFileType.PREDICATES_FILE_TYPE to LootItemCondition.DIRECT_CODEC,
        PackContentFileType.RECIPES_FILE_TYPE to Recipe.CODEC,
        PackContentFileType.CHAT_TYPE_FILE_TYPE to ChatType.DIRECT_CODEC,
        PackContentFileType.DAMAGE_TYPE_FILE_TYPE to DamageType.DIRECT_CODEC,
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
        PackContentFileType.TRIM_MATERIAL_FILE_TYPE to TrimMaterial.DIRECT_CODEC,
        PackContentFileType.TRIM_PATTERN_FILE_TYPE to TrimPattern.DIRECT_CODEC,
        PackContentFileType.WOLF_VARIANT_FILE_TYPE to WolfVariant.DIRECT_CODEC,
        PackContentFileType.PIG_VARIANT_FILE_TYPE to PigVariant.DIRECT_CODEC,
        PackContentFileType.CAT_VARIANT_FILE_TYPE to CatVariant.DIRECT_CODEC,
        PackContentFileType.FROG_VARIANT_FILE_TYPE to FrogVariant.DIRECT_CODEC,
        PackContentFileType.COW_VARIANT_FILE_TYPE to CowVariant.DIRECT_CODEC,
        PackContentFileType.CHICKEN_VARIANT_FILE_TYPE to ChickenVariant.DIRECT_CODEC,
        PackContentFileType.ZOMBIE_NAUTILUS_VARIANT_FILE_TYPE to ZombieNautilusVariant.DIRECT_CODEC,
        PackContentFileType.WOLF_SOUND_VARIANT_FILE_TYPE to WolfSoundVariant.DIRECT_CODEC,
        PackContentFileType.DIMENSION_FILE_TYPE to LevelStem.CODEC,
        PackContentFileType.DIMENSION_TYPE_FILE_TYPE to DimensionType.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_BIOME_FILE_TYPE to Biome.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_CONFIGURED_CARVER_FILE_TYPE to ConfiguredWorldCarver.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_CONFIGURED_FEATURE_FILE_TYPE to ConfiguredFeature.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_DENSITY_FUNCTION_FILE_TYPE to DensityFunction.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_FLAT_LEVEL_GENERATOR_PRESET_FILE_TYPE to FlatLevelGeneratorPreset.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST_FILE_TYPE to MultiNoiseBiomeSourceParameterList.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_NOISE_FILE_TYPE to NormalNoise.NoiseParameters.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_NOISE_SETTINGS_FILE_TYPE to NoiseGeneratorSettings.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_PLACED_FEATURE_FILE_TYPE to PlacedFeature.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_PROCESSOR_LIST_FILE_TYPE to StructureProcessorType.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_STRUCTURE_FILE_TYPE to JigsawStructure.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_STRUCTURE_SET_FILE_TYPE to StructureSet.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_TEMPLATE_POOL_FILE_TYPE to StructureTemplatePool.DIRECT_CODEC,
        PackContentFileType.WORLDGEN_WORLD_PRESET_FILE_TYPE to WorldPreset.DIRECT_CODEC,
        PackContentFileType.PAINTING_VARIANT_FILE_TYPE to PaintingVariant.DIRECT_CODEC,
        PackContentFileType.JUKEBOX_SONG_FILE_TYPE to JukeboxSong.DIRECT_CODEC,
        PackContentFileType.ENCHANTMENT_FILE_TYPE to Enchantment.DIRECT_CODEC,
        PackContentFileType.ENCHANTMENT_PROVIDER_FILE_TYPE to EnchantmentProvider.DIRECT_CODEC,
        PackContentFileType.DIALOG_FILE_TYPE to Dialog.DIRECT_CODEC,
        PackContentFileType.TIMELINE_FILE_TYPE to Timeline.DIRECT_CODEC,
        PackContentFileType.TRIAL_SPAWNER_FILE_TYPE to TrialSpawnerConfig.DIRECT_CODEC,
        PackContentFileType.INSTRUMENT_FILE_TYPE to Instrument.DIRECT_CODEC,
        PackContentFileType.TEST_ENVIRONMENT_FILE_TYPE to TestEnvironmentDefinition.DIRECT_CODEC,
        PackContentFileType.TEST_INSTANCE_FILE_TYPE to GameTestInstance.DIRECT_CODEC,
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