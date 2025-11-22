package net.papierkorb2292.command_crafter

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.advancement.Advancement
import net.minecraft.block.entity.BannerPattern
import net.minecraft.block.jukebox.JukeboxSong
import net.minecraft.command.CommandSource
import net.minecraft.dialog.type.Dialog
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.provider.EnchantmentProvider
import net.minecraft.entity.damage.DamageType
import net.minecraft.entity.decoration.painting.PaintingVariant
import net.minecraft.entity.passive.CatVariant
import net.minecraft.entity.passive.ChickenVariant
import net.minecraft.entity.passive.CowVariant
import net.minecraft.entity.passive.FrogVariant
import net.minecraft.entity.passive.PigVariant
import net.minecraft.entity.passive.WolfSoundVariant
import net.minecraft.entity.passive.WolfVariant
import net.minecraft.item.equipment.trim.ArmorTrimMaterial
import net.minecraft.item.equipment.trim.ArmorTrimPattern
import net.minecraft.loot.LootTable
import net.minecraft.loot.condition.LootCondition
import net.minecraft.loot.function.LootFunctionTypes
import net.minecraft.network.message.MessageType
import net.minecraft.recipe.Recipe
import net.minecraft.registry.Registry
import net.minecraft.registry.tag.TagFile
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.structure.StructureSet
import net.minecraft.structure.pool.StructurePool
import net.minecraft.structure.processor.StructureProcessorType
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameRules
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
import net.papierkorb2292.command_crafter.config.CommandCrafterConfig
import net.papierkorb2292.command_crafter.editor.*
import net.papierkorb2292.command_crafter.editor.debugger.InitializedEventEmittingMessageWrapper
import net.papierkorb2292.command_crafter.editor.debugger.MinecraftDebuggerServer
import net.papierkorb2292.command_crafter.editor.processing.*
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer.Companion.addJsonAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ScoreboardFileAnalyzer
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.ServerScoreboardStorageFileSystem
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.FileSystemResult
import net.papierkorb2292.command_crafter.editor.scoreboardStorageViewer.api.ReadDirectoryResultEntry
import net.papierkorb2292.command_crafter.helper.UnitTypeAdapter
import net.papierkorb2292.command_crafter.mixin.parser.CommandNodeAccessor
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage.Companion.logMacroAnalyzingTime
import org.apache.logging.log4j.LogManager
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Position
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
        MinecraftLanguageServer.addAnalyzer(PackMetaAnalyzer)

        addJsonAnalyzer(PackContentFileType.ADVANCEMENTS_FILE_TYPE, Advancement.CODEC)
        addJsonAnalyzer(PackContentFileType.BANNER_PATTERN_FILE_TYPE, BannerPattern.CODEC)
        addJsonAnalyzer(PackContentFileType.ITEM_MODIFIER_FILE_TYPE, LootFunctionTypes.CODEC)
        addJsonAnalyzer(PackContentFileType.LOOT_TABLES_FILE_TYPE, LootTable.CODEC)
        addJsonAnalyzer(PackContentFileType.PREDICATES_FILE_TYPE, LootCondition.CODEC)
        addJsonAnalyzer(PackContentFileType.RECIPES_FILE_TYPE, Recipe.CODEC)
        addJsonAnalyzer(PackContentFileType.CHAT_TYPE_FILE_TYPE, MessageType.CODEC)
        addJsonAnalyzer(PackContentFileType.DAMAGE_TYPE_FILE_TYPE, DamageType.CODEC)
        addJsonAnalyzer(PackContentFileType.BANNER_PATTERN_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.BLOCK_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.CAT_VARIANT_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.DAMAGE_TYPE_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.ENCHANTMENT_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.ENTITY_TYPE_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.FLUID_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.FUNCTION_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.GAME_EVENT_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.INSTRUMENT_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.ITEM_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.PAINTING_VARIANT_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.POINT_OF_INTEREST_TYPE_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_BIOME_TYPE_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_FLAT_LEVEL_GENERATOR_PRESET_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_STRUCTURE_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_WORLD_PRESET_TAGS_FILE_TYPE, TagFile.CODEC)
        addJsonAnalyzer(PackContentFileType.TRIM_MATERIAL_FILE_TYPE, ArmorTrimMaterial.CODEC)
        addJsonAnalyzer(PackContentFileType.TRIM_PATTERN_FILE_TYPE, ArmorTrimPattern.CODEC)
        addJsonAnalyzer(PackContentFileType.WOLF_VARIANT_FILE_TYPE, WolfVariant.CODEC)
        addJsonAnalyzer(PackContentFileType.PIG_VARIANT_FILE_TYPE, PigVariant.CODEC)
        addJsonAnalyzer(PackContentFileType.CAT_VARIANT_FILE_TYPE, CatVariant.CODEC)
        addJsonAnalyzer(PackContentFileType.FROG_VARIANT_FILE_TYPE, FrogVariant.CODEC)
        addJsonAnalyzer(PackContentFileType.COW_VARIANT_FILE_TYPE, CowVariant.CODEC)
        addJsonAnalyzer(PackContentFileType.CHICKEN_VARIANT_FILE_TYPE, ChickenVariant.CODEC)
        addJsonAnalyzer(PackContentFileType.WOLF_SOUND_VARIANT_FILE_TYPE, WolfSoundVariant.CODEC)
        addJsonAnalyzer(PackContentFileType.DIMENSION_FILE_TYPE, DimensionOptions.CODEC)
        addJsonAnalyzer(PackContentFileType.DIMENSION_TYPE_FILE_TYPE, DimensionType.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_BIOME_FILE_TYPE, Biome.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_CONFIGURED_CARVER_FILE_TYPE, ConfiguredCarver.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_CONFIGURED_FEATURE_FILE_TYPE, ConfiguredFeature.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_DENSITY_FUNCTION_FILE_TYPE, DensityFunction.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_FLAT_LEVEL_GENERATOR_PRESET_FILE_TYPE, FlatLevelGeneratorPreset.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST_FILE_TYPE, MultiNoiseBiomeSourceParameterList.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_NOISE_FILE_TYPE, DensityFunction.Noise.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_NOISE_SETTINGS_FILE_TYPE, ChunkGeneratorSettings.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_PLACED_FEATURE_FILE_TYPE, PlacedFeature.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_PROCESSOR_LIST_FILE_TYPE, StructureProcessorType.PROCESSORS_CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_STRUCTURE_FILE_TYPE, JigsawStructure.STRUCTURE_CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_STRUCTURE_SET_FILE_TYPE, StructureSet.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_TEMPLATE_POOL_FILE_TYPE, StructurePool.CODEC)
        addJsonAnalyzer(PackContentFileType.WORLDGEN_WORLD_PRESET_FILE_TYPE, WorldPreset.CODEC)
        addJsonAnalyzer(PackContentFileType.PAINTING_VARIANT_FILE_TYPE, PaintingVariant.CODEC)
        addJsonAnalyzer(PackContentFileType.JUKEBOX_SONG_FILE_TYPE, JukeboxSong.CODEC)
        addJsonAnalyzer(PackContentFileType.ENCHANTMENT_FILE_TYPE, Enchantment.CODEC)
        addJsonAnalyzer(PackContentFileType.ENCHANTMENT_PROVIDER_FILE_TYPE, EnchantmentProvider.CODEC)
        addJsonAnalyzer(PackContentFileType.DIALOG_FILE_TYPE, Dialog.CODEC)
        MinecraftLanguageServer.addAnalyzer(ScoreboardFileAnalyzer)

        ServerScoreboardStorageFileSystem.registerTickUpdateRunner()
        IdArgumentTypeAnalyzer.registerFileTypeAdditionalDataType()
        DataObjectDecoding.registerDataObjectSourceAdditionalDataType()

        if(FabricLoader.getInstance().environmentType == EnvType.SERVER) {
            // When analyzing is done by a dedicated server, a ServerCommandSource can be used
            // For all other cases, the analyzer is only needed on the client side, where an AnalyzingClientCommandSource is used
            MinecraftLanguageServer.addAnalyzer(object : FileAnalyseHandler {
                override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith(".mcfunction")

                override fun analyze(
                    file: OpenFile,
                    languageServer: MinecraftLanguageServer,
                ): AnalyzingResult {
                    val directServerConnection = languageServer.minecraftServer as? DirectServerConnection
                        ?: throw IllegalArgumentException("ServerConnection on dedicated server was expected to be DirectServerConnection")
                    val dispatcher = languageServer.minecraftServer.commandDispatcher
                    val reader = DirectiveStringReader(
                        file.createFileMappingInfo(),
                        dispatcher,
                        AnalyzingResourceCreator(languageServer, file.uri).apply {
                            (file.persistentAnalyzerData as? AnalyzingResourceCreator.CacheData)?.let { persistentCache ->
                                if(persistentCache.usedCommandDispatcher == dispatcher)
                                    previousCache = persistentCache
                            }
                            newCache.usedCommandDispatcher = dispatcher
                        }
                    )
                    val result = AnalyzingResult(reader.fileMappingInfo, Position())
                    reader.resourceCreator.resourceStack.push(AnalyzingResourceCreator.ResourceStackEntry(result))
                    val source = ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, directServerConnection.server.overworld, 2, "", ScreenTexts.EMPTY, directServerConnection.server, null)
                    LanguageManager.analyse(reader, source, result, Language.TopLevelClosure(VanillaLanguage()))
                    reader.resourceCreator.resourceStack.pop()
                    result.clearDisabledFeatures(languageServer.featureConfig, listOf(LanguageManager.ANALYZER_CONFIG_PATH, ""))
                    if(!Thread.currentThread().isInterrupted)
                        file.persistentAnalyzerData = reader.resourceCreator.newCache
                    return result
                }
            })

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
                val source = ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, args.permissionLevel ?: 2, "", ScreenTexts.EMPTY, null, null)
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
        val shortenNbtGameRule = GameRuleFactory.createBooleanRule(true) { _, rule ->
            shortenNbt = rule.get()
        }
        val shortenNbtGameRuleKey = GameRuleRegistry.register("shortenNbt", GameRules.Category.CHAT, shortenNbtGameRule)
        ServerLifecycleEvents.SERVER_STARTED.register {
            shortenNbt = it.gameRules.getBoolean(shortenNbtGameRuleKey)
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
                featureConfig: FeatureConfig
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
                featureConfig: FeatureConfig
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