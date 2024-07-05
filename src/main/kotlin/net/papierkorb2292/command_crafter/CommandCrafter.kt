package net.papierkorb2292.command_crafter

import com.mojang.brigadier.CommandDispatcher
import com.mojang.serialization.Codec
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry
import net.minecraft.advancement.Advancement
import net.minecraft.block.entity.BannerPattern
import net.minecraft.block.jukebox.JukeboxSong
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.FontManager
import net.minecraft.client.texture.atlas.AtlasSourceManager
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.provider.EnchantmentProvider
import net.minecraft.entity.damage.DamageType
import net.minecraft.entity.decoration.painting.PaintingVariant
import net.minecraft.entity.passive.WolfVariant
import net.minecraft.item.trim.ArmorTrimMaterial
import net.minecraft.item.trim.ArmorTrimPattern
import net.minecraft.loot.LootTable
import net.minecraft.loot.condition.LootCondition
import net.minecraft.loot.function.LootFunctionTypes
import net.minecraft.network.message.MessageType
import net.minecraft.recipe.Recipe
import net.minecraft.registry.CombinedDynamicRegistries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryLoader
import net.minecraft.registry.ServerDynamicRegistryType
import net.minecraft.registry.tag.TagFile
import net.minecraft.resource.LifecycledResourceManagerImpl
import net.minecraft.resource.ResourceType
import net.minecraft.resource.VanillaDataPackProvider
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.structure.StructureSet
import net.minecraft.structure.pool.StructurePool
import net.minecraft.structure.processor.StructureProcessorType
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
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
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.*
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer.Companion.addJsonAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.apache.logging.log4j.LogManager
import org.eclipse.lsp4j.Position
import java.io.BufferedReader

object CommandCrafter: ModInitializer {
    const val MOD_ID = "command_crafter"
    val LOGGER = LogManager.getLogger(MOD_ID)

    override fun onInitialize() {

        initializeEditor()
        NetworkServerConnection.registerServerPacketHandlers()

        initializeParser()
        LOGGER.info("Loaded CommandCrafter!")
    }

    private fun initializeEditor() {
        MinecraftLanguageServer.addAnalyzer(object: FileAnalyseHandler {
            override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith(".mcfunction")

            override fun analyze(
                file: OpenFile,
                languageServer: MinecraftLanguageServer
            ): AnalyzingResult {
                val reader = DirectiveStringReader(FileMappingInfo(file.stringifyLines()), languageServer.minecraftServer.commandDispatcher, AnalyzingResourceCreator(languageServer, file.uri))
                val result = AnalyzingResult(reader.fileMappingInfo, Position())
                reader.resourceCreator.resourceStack.push(AnalyzingResourceCreator.ResourceStackEntry(result))
                val source = AnalyzingClientCommandSource(MinecraftClient.getInstance())
                LanguageManager.analyse(reader, source, result, Language.TopLevelClosure(VanillaLanguage()))
                return result
            }
        })

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
    }

    private fun initializeParser() {
        Registry.register(LanguageManager.LANGUAGES, Identifier.of(VanillaLanguage.ID), VanillaLanguage.VanillaLanguageType)
        ArgumentTypeRegistry.registerArgumentType(Identifier.of(MOD_ID, "datapack_build_args"), DatapackBuildArgs.DatapackBuildArgsArgumentType.javaClass, ConstantArgumentSerializer.of { -> DatapackBuildArgs.DatapackBuildArgsArgumentType })
        RawZipResourceCreator.DATA_TYPE_PROCESSORS += object : RawZipResourceCreator.DataTypeProcessor {
            override val type: String
                get() = "functions"

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

    val defaultDynamicRegistryManager: CombinedDynamicRegistries<ServerDynamicRegistryType> by lazy {
        val initialRegistries = ServerDynamicRegistryType.createCombinedDynamicRegistries();
        val precedingWorldgen = initialRegistries.getPrecedingRegistryManagers(ServerDynamicRegistryType.WORLDGEN);
        return@lazy initialRegistries.with(
            ServerDynamicRegistryType.RELOADABLE,
            RegistryLoader.loadFromResource(
                LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, listOf(VanillaDataPackProvider.createDefaultPack())),
                precedingWorldgen,
                RegistryLoader.DYNAMIC_REGISTRIES
            )
        )
    }
}