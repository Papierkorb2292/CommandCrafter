package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.editor.EditorClientFileFinder
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.processing.helper.getKeywordsFromPath
import net.papierkorb2292.command_crafter.editor.processing.helper.standardizeKeyword
import net.papierkorb2292.command_crafter.networking.enumConstantCodec
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name

enum class PackContentFileType(val contentTypePath: String, val packType: PackType, val keywords: List<String>) {
    FUNCTIONS_FILE_TYPE("function", PackType.DATA),
    ADVANCEMENTS_FILE_TYPE("advancement", PackType.DATA),
    BANNER_PATTERN_FILE_TYPE("banner_pattern", PackType.DATA),
    ITEM_MODIFIER_FILE_TYPE("item_modifier", PackType.DATA),
    LOOT_TABLES_FILE_TYPE("loot_table", PackType.DATA),
    PREDICATES_FILE_TYPE("predicate", PackType.DATA),
    RECIPES_FILE_TYPE("recipe", PackType.DATA),
    STRUCTURES_FILE_TYPE("structure", PackType.DATA),
    CHAT_TYPE_FILE_TYPE("chat_type", PackType.DATA),
    DAMAGE_TYPE_FILE_TYPE("damage_type", PackType.DATA),
    BANNER_PATTERN_TAGS_FILE_TYPE("tags/banner_pattern", PackType.DATA),
    BLOCK_TAGS_FILE_TYPE("tags/block", PackType.DATA),
    CAT_VARIANT_TAGS_FILE_TYPE("tags/cat_variant", PackType.DATA),
    DAMAGE_TYPE_TAGS_FILE_TYPE("tags/damage_type", PackType.DATA),
    ENCHANTMENT_TAGS_FILE_TYPE("tags/enchantment", PackType.DATA),
    ENTITY_TYPE_TAGS_FILE_TYPE("tags/entity_type", PackType.DATA),
    FLUID_TAGS_FILE_TYPE("tags/fluid", PackType.DATA),
    FUNCTION_TAGS_FILE_TYPE("tags/function", PackType.DATA),
    GAME_EVENT_TAGS_FILE_TYPE("tags/game_event", PackType.DATA),
    INSTRUMENT_TAGS_FILE_TYPE("tags/instrument", PackType.DATA),
    ITEM_TAGS_FILE_TYPE("tags/item", PackType.DATA),
    PAINTING_VARIANT_TAGS_FILE_TYPE("tags/painting_variant", PackType.DATA),
    POINT_OF_INTEREST_TYPE_TAGS_FILE_TYPE("tags/point_of_interest_type", PackType.DATA),
    WORLDGEN_BIOME_TYPE_TAGS_FILE_TYPE("tags/worldgen/biome", PackType.DATA),
    WORLDGEN_FLAT_LEVEL_GENERATOR_PRESET_TAGS_FILE_TYPE("tags/worldgen/flat_level_generator_preset", PackType.DATA),
    WORLDGEN_STRUCTURE_TAGS_FILE_TYPE("tags/structure", PackType.DATA),
    WORLDGEN_WORLD_PRESET_TAGS_FILE_TYPE("tags/world_preset", PackType.DATA),
    TRIM_MATERIAL_FILE_TYPE("trim_material", PackType.DATA),
    TRIM_PATTERN_FILE_TYPE("trim_pattern", PackType.DATA),
    WOLF_VARIANT_FILE_TYPE("wolf_variant", PackType.DATA),
    PIG_VARIANT_FILE_TYPE("pig_variant", PackType.DATA),
    CAT_VARIANT_FILE_TYPE("cat_variant", PackType.DATA),
    FROG_VARIANT_FILE_TYPE("frog_variant", PackType.DATA),
    COW_VARIANT_FILE_TYPE("cow_variant", PackType.DATA),
    CHICKEN_VARIANT_FILE_TYPE("chicken_variant", PackType.DATA),
    WOLF_SOUND_VARIANT_FILE_TYPE("wolf_sound_variant", PackType.DATA),
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
    PAINTING_VARIANT_FILE_TYPE("painting_variant", PackType.DATA),
    JUKEBOX_SONG_FILE_TYPE("jukebox_song", PackType.DATA),
    ENCHANTMENT_FILE_TYPE("enchantment", PackType.DATA),
    ENCHANTMENT_PROVIDER_FILE_TYPE("enchantment_provider", PackType.DATA),

    ATLASES_FILE_TYPE("atlases", PackType.RESOURCE),
    BLOCKSTATES_FILE_TYPE("blockstates", PackType.RESOURCE),
    EQUIPMENT_FILE_TYPE("equipment", PackType.RESOURCE),
    FONTS_FILE_TYPE("font", PackType.RESOURCE),
    ITEMS_FILE_TYPE("items", PackType.RESOURCE),
    LANGUAGES_FILE_TYPE("lang", PackType.RESOURCE),
    MODELS_FILE_TYPE("models", PackType.RESOURCE),
    PARTICLES_FILE_TYPE("particles", PackType.RESOURCE),
    POST_EFFECTS_FILE_TYPE("post_effect", PackType.RESOURCE),
    CORE_SHADERS_FILE_TYPE("shaders/core", PackType.RESOURCE),
    INCLUDE_SHADERS_FILE_TYPE("shaders/include", PackType.RESOURCE),
    POST_SHADERS_FILE_TYPE("shaders/post", PackType.RESOURCE),
    SOUNDS_FILE_TYPE("sounds", PackType.RESOURCE),
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
                    val resourceId = Identifier.of(
                        path.getName(i + 1).name,
                        path.subpath(j, path.nameCount).name.replace('\\', '/')
                    ) ?: continue
                    val packPath = path.subpath(0, i).toString()
                    return ParsedPath(resourceId, packPath, type)
                }
            }
            return null
        }

        fun parsePath(path: String): ParsedPath? {
            val segments = path.split('/', '\\')
            for(i in 0 until segments.size - 2) {
                val currentFolder = segments[i]
                if(currentFolder !in packTypeFolders) continue
                for(j in segments.size - 1 downTo i + 3) {
                    val potentialContentTypePath = segments.subList(i + 2, j).joinToString("/")
                    val type = types[potentialContentTypePath] ?: continue
                    if(type.packType.folderName != currentFolder) continue
                    val resourceId = Identifier.of(
                        segments[i + 1],
                        segments.subList(j, segments.size).joinToString("/")
                    ) ?: continue
                    val packPath = segments.subList(0, i).joinToString("/")
                    return ParsedPath(resourceId, packPath, type)
                }
            }
            return null
        }

        fun findWorkspaceResourceFromId(id: Identifier, editorClientFileFinder: EditorClientFileFinder, keywords: Set<String>): CompletableFuture<Pair<PackContentFileType, String>?>
            = findWorkspaceResourceFromId(PackagedId(id, "**"), editorClientFileFinder, keywords)

        fun findWorkspaceResourceFromId(packagedId: PackagedId, editorClientFileFinder: EditorClientFileFinder, keywords: Set<String>): CompletableFuture<Pair<PackContentFileType, String>?> {
            val id = packagedId.resourceId
            val dotIndex = id.path.lastIndexOf('.')
            val fileExtensionPath =
                if(dotIndex == -1 || dotIndex < id.path.lastIndexOf('/')) "${id.path}.*"
                else id.path
            return editorClientFileFinder
                .findFiles("${packagedId.packPath}/${id.namespace}/*/**/$fileExtensionPath") // Uses /*/**/ to guarantee that there's at least one folder between namespace and path to determine the file type
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

        fun findWorkspaceResourceFromIdAndPackContentFileType(id: Identifier, type: PackContentFileType, editorClientFileFinder: EditorClientFileFinder): CompletableFuture<String?>
            = findWorkspaceResourceFromIdAndPackContentFileType(PackagedId(id, "**"), type, editorClientFileFinder)

        fun findWorkspaceResourceFromIdAndPackContentFileType(packagedId: PackagedId, type: PackContentFileType, editorClientFileFinder: EditorClientFileFinder): CompletableFuture<String?> {
            val id = packagedId.resourceId
            val dotIndex = id.path.lastIndexOf('.')
            val fileExtensionPath =
                if(dotIndex == -1 || dotIndex < id.path.lastIndexOf('/')) "${id.path}.*"
                else id.path
            return editorClientFileFinder
                .findFiles(type.toStringPath(id.namespace, fileExtensionPath, packagedId.packPath))
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

    fun toStringPath(id: PackagedId) =
        toStringPath(id.resourceId.namespace, id.resourceId.path, id.packPath)
    fun toStringPath(namespace: String, path: String, packPath: String) =
        "${packPath}/${packType.folderName}/$namespace/$contentTypePath/$path"

    data class ParsedPath(val id: PackagedId, val type: PackContentFileType) {
        constructor(resourceId: Identifier, packPath: String, type: PackContentFileType) : this(PackagedId(resourceId, packPath), type)
    }

    enum class PackType(val folderName: String) {
        DATA("data"), RESOURCE("assets")
    }
}