package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.EditorClientFileFinder
import net.papierkorb2292.command_crafter.editor.processing.helper.getKeywordsFromPath
import net.papierkorb2292.command_crafter.editor.processing.helper.standardizeKeyword
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name

enum class PackContentFileType(val contentTypePath: String, val packType: PackType, val keywords: List<String>) {
    FUNCTIONS_FILE_TYPE("functions", PackType.DATA),
    ADVANCEMENTS_FILE_TYPE("advancements", PackType.DATA),
    BANNER_PATTERN_FILE_TYPE("banner_pattern", PackType.DATA),
    ITEM_MODIFIER_FILE_TYPE("item_modifiers", PackType.DATA),
    LOOT_TABLES_FILE_TYPE("loot_tables", PackType.DATA),
    PREDICATES_FILE_TYPE("predicates", PackType.DATA),
    RECIPES_FILE_TYPE("recipes", PackType.DATA),
    STRUCTURES_FILE_TYPE("structures", PackType.DATA),
    CHAT_TYPE_FILE_TYPE("chat_type", PackType.DATA),
    DAMAGE_TYPE_FILE_TYPE("damage_type", PackType.DATA),
    BANNER_PATTERN_TAGS_FILE_TYPE("tags/banner_pattern", PackType.DATA),
    BLOCK_TAGS_FILE_TYPE("tags/blocks", PackType.DATA),
    CAT_VARIANT_TAGS_FILE_TYPE("tags/cat_variant", PackType.DATA),
    DAMAGE_TYPE_TAGS_FILE_TYPE("tags/damage_type", PackType.DATA),
    ENCHANTMENT_TAGS_FILE_TYPE("tags/enchantment", PackType.DATA),
    ENTITY_TYPE_TAGS_FILE_TYPE("tags/entity_type", PackType.DATA),
    FLUID_TAGS_FILE_TYPE("tags/fluids", PackType.DATA),
    FUNCTION_TAGS_FILE_TYPE("tags/functions", PackType.DATA),
    GAME_EVENT_TAGS_FILE_TYPE("tags/game_events", PackType.DATA),
    INSTRUMENT_TAGS_FILE_TYPE("tags/instrument", PackType.DATA),
    ITEM_TAGS_FILE_TYPE("tags/items", PackType.DATA),
    PAINTING_VARIANT_TAGS_FILE_TYPE("tags/painting_variant", PackType.DATA),
    POINT_OF_INTEREST_TYPE_TAGS_FILE_TYPE("tags/point_of_interest_type", PackType.DATA),
    WORLDGEN_BIOME_TYPE_TAGS_FILE_TYPE("tags/worldgen/biome", PackType.DATA),
    WORLDGEN_FLAT_LEVEL_GENERATOR_PRESET_TAGS_FILE_TYPE("tags/worldgen/flat_level_generator_preset", PackType.DATA),
    WORLDGEN_STRUCTURE_TAGS_FILE_TYPE("tags/structure", PackType.DATA),
    WORLDGEN_WORLD_PRESET_TAGS_FILE_TYPE("tags/world_preset", PackType.DATA),
    TRIM_MATERIAL_FILE_TYPE("trim_material", PackType.DATA),
    TRIM_PATTERN_FILE_TYPE("trim_pattern", PackType.DATA),
    WOLF_VARIANT_FILE_TYPE("wolf_variant", PackType.DATA),
    DIMENSION_FILE_TYPE("dimension", PackType.DATA),
    DIMENSION_TYPE_FILE_TYPE("dimension_type", PackType.DATA),
    WORLDGEN_BIOME_FILE_TYPE("worldgen/biome", PackType.DATA),
    WORLDGEN_CONFIGURED_CARVER_FILE_TYPE("worldgen/configured_carver", PackType.DATA),
    WORLDGEN_CONFIGURED_FEATURE_FILE_TYPE("worldgen/configured_feature", PackType.DATA),
    WORLDGEN_DENSITY_FUNCTION_FILE_TYPE("worldgen/density_function", PackType.DATA),
    WORLDGEN_FLAT_LEVEL_GENERATOR_PRESET_FILE_TYPE("worldgen/flat_level_generator_preset", PackType.DATA),
    WORLDGEN_MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST_FILE_TYPE("worldgen/multi_noise_biome_source", PackType.DATA),
    WORLDGEN_NOISE_FILE_TYPE("worldgen/noise", PackType.DATA),
    WORLDGEN_NOISE_SETTINGS_FILE_TYPE("worldgen/noise_settings", PackType.DATA),
    WORLDGEN_PLACED_FEATURE_FILE_TYPE("worldgen/placed_feature", PackType.DATA),
    WORLDGEN_PROCESSOR_LIST_FILE_TYPE("worldgen/processor_list", PackType.DATA),
    WORLDGEN_STRUCTURE_FILE_TYPE("worldgen/structure", PackType.DATA),
    WORLDGEN_STRUCTURE_SET_FILE_TYPE("worldgen/structure_set", PackType.DATA),
    WORLDGEN_TEMPLATE_POOL_FILE_TYPE("worldgen/template_pool", PackType.DATA),
    WORLDGEN_WORLD_PRESET_FILE_TYPE("worldgen/world_preset", PackType.DATA),

    ATLASES_FILE_TYPE("atlases", PackType.RESOURCE),
    BLOCKSTATES_FILE_TYPE("blockstates", PackType.RESOURCE),
    FONTS_FILE_TYPE("font", PackType.RESOURCE),
    LANGUAGES_FILE_TYPE("lang", PackType.RESOURCE),
    MODELS_FILE_TYPE("models", PackType.RESOURCE),
    PARTICLES_FILE_TYPE("particles", PackType.RESOURCE),
    CORE_SHADERS_FILE_TYPE("shaders/core", PackType.RESOURCE),
    INCLUDE_SHADERS_FILE_TYPE("shaders/include", PackType.RESOURCE),
    POST_SHADERS_FILE_TYPE("shaders/post", PackType.RESOURCE),
    PROGRAM_SHADERS_FILE_TYPE("shaders/program", PackType.RESOURCE),
    TEXTS_FILE_TYPE("texts", PackType.RESOURCE),
    TEXTURES_FILE_TYPE("textures", PackType.RESOURCE);

    constructor(contentTypePath: String, packType: PackType) : this(contentTypePath, packType, getKeywordsFromPath(contentTypePath))

    companion object {
        val types = entries.associateBy { it.contentTypePath }
        val keywords = types.values.flatMap { it.keywords }.toSet()

        val packTypeFolders = PackType.entries.associateBy { it.folderName }
        val PACKET_CODEC = enumConstantCodec(PackContentFileType::class.java)

        fun parsePath(path: Path): ParsedPath? {
            for(i in 0 until path.nameCount - 2) {
                val currentFolder = path.getName(i).toString()
                if(currentFolder !in packTypeFolders) continue
                for(j in path.nameCount - 1 downTo i + 1) {
                    val potentialContentTypePath = path.subpath(i + 1, j).name.replace('\\', '/')
                    val type = types[potentialContentTypePath] ?: continue
                    if(type.packType.folderName != currentFolder) continue
                    val id = Identifier.of(
                        path.getName(i + 1).name,
                        path.subpath(j, path.nameCount).name.replace('\\', '/')
                    ) ?: continue
                    return ParsedPath(id, type)
                }
            }
            return null
        }

        fun parsePath(path: String): ParsedPath? {
            val segments = path.split('/', '\\')
            for(i in 0 until segments.size - 2) {
                val currentFolder = segments[i]
                if(currentFolder !in packTypeFolders) continue
                for(j in segments.size - 1 downTo i + 1) {
                    val potentialContentTypePath = segments.subList(i + 2, j).joinToString("/")
                    val type = types[potentialContentTypePath] ?: continue
                    if(type.packType.folderName != currentFolder) continue
                    val id = Identifier.of(
                        segments[i + 1],
                        segments.subList(j, segments.size).joinToString("/")
                    ) ?: continue
                    return ParsedPath(id, type)
                }
            }
            return null
        }

        fun findWorkspaceResourceFromId(id: Identifier, editorClientFileFinder: EditorClientFileFinder, keywords: Set<String>): CompletableFuture<Pair<PackContentFileType, String>?> {
            val dotIndex = id.path.lastIndexOf('.')
            val fileExtensionPath =
                if(dotIndex == -1 || dotIndex < id.path.lastIndexOf('/')) "${id.path}.*"
                else id.path
            return editorClientFileFinder
                .findFiles("**/${id.namespace}/**/$fileExtensionPath")
                .thenApply { uris ->
                    uris.mapNotNull { uri ->
                        val idPathStart = uri.lastIndexOf(id.path)
                        val pathToContentTypeFiles = uri.substring(0, idPathStart - 1)
                        val namespaceFolder = "/${id.namespace}/"
                        val packTypeFolderEnd = pathToContentTypeFiles.lastIndexOf(namespaceFolder)
                        val packTypeFolderStart = pathToContentTypeFiles.lastIndexOf('/', packTypeFolderEnd - 1)
                        val packTypeFolder = pathToContentTypeFiles.substring(packTypeFolderStart + 1, packTypeFolderEnd)
                        val contentTypePath = pathToContentTypeFiles.substring(packTypeFolderEnd + namespaceFolder.length)
                        val type = types[contentTypePath] ?: return@mapNotNull null
                        if(type.packType.folderName != packTypeFolder) {
                            return@mapNotNull null
                        }
                        type to uri
                    }.maxByOrNull { it.first.keywords.count { keyword -> keyword in keywords } }
                }
        }

        fun findWorkspaceResourceFromIdAndPackContentFileType(id: Identifier, type: PackContentFileType, editorClientFileFinder: EditorClientFileFinder): CompletableFuture<String?> {
            val dotIndex = id.path.lastIndexOf('.')
            val fileExtensionPath =
                if(dotIndex == -1 || dotIndex < id.path.lastIndexOf('/')) "${id.path}.*"
                else id.path
            return editorClientFileFinder
                .findFiles("**/${type.toStringPath(id.namespace, fileExtensionPath)}")
                .thenApply { it.firstOrNull() }
        }

        fun parseKeywords(string: String, startToLeftCursor: Int, startToRightCursor: Int): List<String> {
            val readToRightReader = StringReader(string)
            val resultKeywords = mutableListOf<String>()
            readToRightReader.cursor = startToRightCursor
            while(readToRightReader.canRead()) {
                val rightKeyword = standardizeKeyword(readToRightReader.readUnquotedString())
                if(rightKeyword.isEmpty()) {
                    readToRightReader.skip()
                    continue
                }
                if(rightKeyword !in keywords)
                    break
                resultKeywords += rightKeyword
            }
            val readToLeftReader = StringReader(string.substring(0, startToLeftCursor).reversed())
            while(readToLeftReader.canRead()) {
                val leftKeyword = standardizeKeyword(readToLeftReader.readUnquotedString().reversed())
                if(leftKeyword.isEmpty()) {
                    readToLeftReader.skip()
                    continue
                }
                if(leftKeyword !in keywords)
                    break
                resultKeywords += leftKeyword
            }
            return resultKeywords
        }
    }

    fun toPath(id: Identifier): Path {
        return Path.of(packType.folderName, id.namespace, contentTypePath, id.path)
    }
    fun toStringPath(id: Identifier) =
        toStringPath(id.namespace, id.path)
    fun toStringPath(namespace: String, path: String) =
        "${packType.folderName}/$namespace/$contentTypePath/$path"

    data class ParsedPath(val id: Identifier, val type: PackContentFileType)

    enum class PackType(val folderName: String) {
        DATA("data"), RESOURCE("assets")
    }
}