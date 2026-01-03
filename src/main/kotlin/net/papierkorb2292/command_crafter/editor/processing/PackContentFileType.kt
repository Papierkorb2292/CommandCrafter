package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.StringReader
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.ServerFunctionLibrary
import net.papierkorb2292.command_crafter.editor.EditorClientFileFinder
import net.papierkorb2292.command_crafter.editor.PackagedId
import net.papierkorb2292.command_crafter.editor.processing.helper.getKeywordsFromPath
import net.papierkorb2292.command_crafter.editor.processing.helper.standardizeKeyword
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.name

class PackContentFileType private constructor(val contentTypePath: String, val packType: PackType, val keywords: List<String>) {
    private constructor(contentTypePath: String, packType: PackType) : this(contentTypePath, packType, getKeywordsFromPath(contentTypePath))

    init {
        mutableEntries += this
        mutableTypes[contentTypePath] = this
        mutableKeywords += keywords
    }

    companion object {
        private val mutableEntries = mutableListOf<PackContentFileType>()
        private val mutableTypes = mutableMapOf<String, PackContentFileType>()
        private val mutableKeywords = mutableSetOf<String>()
        val entries: List<PackContentFileType> get() = mutableEntries
        val types: Map<String, PackContentFileType> get() = mutableTypes
        val keywords: Set<String> get() = mutableKeywords

        val FUNCTIONS_FILE_TYPE = getOrCreateTypeForDynamicRegistry(ServerFunctionLibrary.TYPE_KEY)
        val FUNCTION_TAGS_FILE_TYPE = getOrCreateTypeForRegistryTag(ServerFunctionLibrary.TYPE_KEY)
        val ADVANCEMENTS_FILE_TYPE = getOrCreateTypeForDynamicRegistry(Registries.ADVANCEMENT)
        val ITEM_MODIFIER_FILE_TYPE = getOrCreateTypeForDynamicRegistry(Registries.ITEM_MODIFIER)
        val LOOT_TABLES_FILE_TYPE = getOrCreateTypeForDynamicRegistry(Registries.LOOT_TABLE)
        val PREDICATES_FILE_TYPE = getOrCreateTypeForDynamicRegistry(Registries.PREDICATE)
        val RECIPES_FILE_TYPE = getOrCreateTypeForDynamicRegistry(Registries.RECIPE)
        val STRUCTURES_FILE_TYPE = PackContentFileType("structure", PackType.DATA)

        val ATLASES_FILE_TYPE = PackContentFileType("atlases", PackType.RESOURCE)
        val BLOCKSTATES_FILE_TYPE = PackContentFileType("blockstates", PackType.RESOURCE)
        val EQUIPMENT_FILE_TYPE = PackContentFileType("equipment", PackType.RESOURCE)
        val FONTS_FILE_TYPE = PackContentFileType("font", PackType.RESOURCE)
        val ITEMS_FILE_TYPE = PackContentFileType("items", PackType.RESOURCE)
        val LANGUAGES_FILE_TYPE = PackContentFileType("lang", PackType.RESOURCE)
        val MODELS_FILE_TYPE = PackContentFileType("models", PackType.RESOURCE)
        val PARTICLES_FILE_TYPE = PackContentFileType("particles", PackType.RESOURCE)
        val POST_EFFECTS_FILE_TYPE = PackContentFileType("post_effect", PackType.RESOURCE)
        val CORE_SHADERS_FILE_TYPE = PackContentFileType("shaders/core", PackType.RESOURCE)
        val INCLUDE_SHADERS_FILE_TYPE = PackContentFileType("shaders/include", PackType.RESOURCE)
        val POST_SHADERS_FILE_TYPE = PackContentFileType("shaders/post", PackType.RESOURCE)
        val SOUNDS_FILE_TYPE = PackContentFileType("sounds", PackType.RESOURCE)
        val TEXTS_FILE_TYPE = PackContentFileType("texts", PackType.RESOURCE)
        val TEXTURES_FILE_TYPE = PackContentFileType("textures", PackType.RESOURCE)
        val WAYPOINT_STYLE_FILE_TYPE = PackContentFileType("waypoint_style", PackType.RESOURCE)

        val packTypeFolders = PackType.entries.associateBy { it.folderName }
        val PACKET_CODEC = ByteBufCodecs.STRING_UTF8.map({ types[it]!! }, PackContentFileType::contentTypePath)

        fun getOrCreateTypeForDynamicRegistry(key: ResourceKey<out Registry<*>>): PackContentFileType {
            // Note that Fabric mixes into elementsDirPath to add namespace
            val path = Registries.elementsDirPath(key)
            val existing = types[path]
            if(existing != null) return existing
            // Will be registered in constructor
            return PackContentFileType(path, PackType.DATA)
        }
        fun getOrCreateTypeForRegistryTag(key: ResourceKey<out Registry<*>>): PackContentFileType {
            // Note that Fabric mixes into elementsDirPath to add namespace
            val path = Registries.tagsDirPath(key)
            val existing = types[path]
            if(existing != null) return existing
            // Will be registered in constructor
            return PackContentFileType(path, PackType.DATA)
        }

        fun parsePath(path: Path): ParsedPath? {
            for(i in 0 until path.nameCount - 2) {
                val currentFolder = path.getName(i).toString()
                if(currentFolder !in packTypeFolders) continue
                for(j in path.nameCount - 1 downTo i + 1) {
                    val potentialContentTypePath = path.subpath(i + 1, j).name.replace('\\', '/')
                    val type = types[potentialContentTypePath] ?: continue
                    if(type.packType.folderName != currentFolder) continue
                    val resourceId = Identifier.tryBuild(
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
                    val resourceId = Identifier.tryBuild(
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