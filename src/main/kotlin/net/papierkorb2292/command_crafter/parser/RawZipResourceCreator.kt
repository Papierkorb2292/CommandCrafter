package net.papierkorb2292.command_crafter.parser

import com.google.common.io.Files
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.datafixers.util.Either
import net.minecraft.resource.*
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.mixin.parser.DirectoryResourcePackAccessor
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.helper.ZipFileProvider
import java.io.BufferedReader
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RawZipResourceCreator {
    companion object {
        private val UNKNOWN_DATAPACK_EXCEPTION = DynamicCommandExceptionType { Text.of("Datapack $it is of unknown type and cannot be build") }
        private val PROCESSOR_EXCEPTION = Dynamic2CommandExceptionType { id, exception -> Text.of("Error while processing resource '$id' for datapack build: $exception") }
        private val MISSING_PACK_META_EXCEPTION = SimpleCommandExceptionType(Text.of("Datapack is missing pack.mcmeta"))

        val DATA_TYPE_PROCESSORS: MutableList<DataTypeProcessor> = ArrayList()

        fun buildDatapack(pack: ResourcePack, args: DatapackBuildArgs, dispatcher: CommandDispatcher<ServerCommandSource>, output: ZipOutputStream) {
            val resourceCreator = RawZipResourceCreator()
            when (pack) {
                is DirectoryResourcePack -> {
                    val dataRoot = (pack as DirectoryResourcePackAccessor).root.resolve(ResourceType.SERVER_DATA.directory)
                    for(namespace in pack.getNamespaces(ResourceType.SERVER_DATA)) {
                        DirectoryResourcePack.findResources(namespace, dataRoot.resolve(namespace), emptyList()) { fileId, content ->
                            processFile(
                                fileId,
                                content,
                                resourceCreator,
                                args,
                                dispatcher
                            )
                        }
                    }
                }
                is ZipResourcePack -> {
                    val zipFile = (pack as ZipFileProvider).`command_crafter$getZipFile`()
                    val dataDirectory = ResourceType.SERVER_DATA.directory
                    for(entry in zipFile.entries()) {
                        if(!entry.name.startsWith(dataDirectory)) continue
                        val entryPath = Path.of(dataDirectory).relativize(Path.of(entry.name))
                        if(entryPath.parent == null) continue
                        val namespace = entryPath.getName(0)
                        val path = namespace.relativize(entryPath)
                        processFile(
                            Identifier(namespace.toString(), path.toString().replace('\\', '/')),
                            InputSupplier.create(zipFile, entry),
                            resourceCreator,
                            args,
                            dispatcher
                        )
                    }
                }
                else -> throw UNKNOWN_DATAPACK_EXCEPTION.create(pack.name)
            }

            val packMeta = (pack.openRoot(ResourcePack.PACK_METADATA_NAME)
                ?: throw MISSING_PACK_META_EXCEPTION.create())
                .get()
            output.putNextEntry(ZipEntry(ResourcePack.PACK_METADATA_NAME))
            packMeta.copyTo(output)
            packMeta.close()

            resourceCreator.createResources(output)
        }

        private fun processFile(
            fileId: Identifier,
            content: InputSupplier<InputStream>,
            resourceCreator: RawZipResourceCreator,
            args: DatapackBuildArgs,
            dispatcher: CommandDispatcher<ServerCommandSource>,
        ) {
            val resourceExtension = Files.getFileExtension(fileId.path)
            val resourceId = Identifier(fileId.namespace, fileId.path.substring(0, fileId.path.length - resourceExtension.length - 1))
            for(processor in DATA_TYPE_PROCESSORS) {
                if (resourceId.path.startsWith(processor.type)) {
                    val input = content.get()
                    val reader = input.bufferedReader()
                    val path = Path.of(resourceId.path)
                    val id = Identifier(resourceId.namespace, Path.of(processor.type).relativize(path).toString())
                    try {
                        if (processor.shouldProcess(args)) {
                            processor.process(args, id, reader, resourceCreator, dispatcher)
                            reader.close()
                            return
                        }
                        processor.validate(args, id, reader, dispatcher)
                    } catch (e: Exception) {
                        throw PROCESSOR_EXCEPTION.create(fileId, e.message)
                    }
                }
            }
            val input = content.get()
            RawResource(RawResource.RawResourceType("", resourceExtension)).run {
                this.content += Either.left(String(input.readAllBytes()))
                resourceCreator.resources += resourceId to this
            }
            input.close()
        }

        private fun numberSubResource(map: MutableMap<String, Int>, type: String): Int {
            return (map[type] ?: 0).also { map[type] = it + 1 }
        }
    }

    private val resources: MutableList<Pair<Identifier, RawResource>> = ArrayList()

    val resourceStack = LinkedList<RawResource>()

    fun addResource(id: Identifier, resource: RawResource) {
        resources += id to resource
    }

    private fun createResources(zipOutput: ZipOutputStream) {
        val subResourceNumbering: HashMap<String, Int> = HashMap()
        for((id, resource) in resources) {
            createResource(id, id, resource, zipOutput, subResourceNumbering)
            subResourceNumbering.clear()
        }
    }

    private fun createResource(currentId: Identifier, parentFunctionId: Identifier, resource: RawResource, zipOutput: ZipOutputStream, subResourceNumbering: MutableMap<String, Int>) {
        val resourceId = Identifier(currentId.namespace, Path.of(resource.type.prefix).resolve(currentId.path).toString().replace('\\', '/'))
        resource.id = resourceId
        resource.content.map { either ->
            either.map({ it }, content@{
                it.id?.run { return@content toString() }
                val childId = Identifier(parentFunctionId.namespace, "${parentFunctionId.path}--${numberSubResource(subResourceNumbering, it.type.prefix)}--craftergen")
                createResource(childId, parentFunctionId, it, zipOutput, subResourceNumbering)
                childId.toString()
            })
        }.apply {
            zipOutput.putNextEntry(ZipEntry("${Path.of("${ResourceType.SERVER_DATA.directory}/${resourceId.namespace}").resolve(resourceId.path)}.${resource.type.fileExtension}"))
            zipOutput.writer(Charset.defaultCharset()).append(*toTypedArray()).flush()
        }
    }

    interface DataTypeProcessor {
        val type: String
        fun shouldProcess(args: DatapackBuildArgs): Boolean
        fun process(args: DatapackBuildArgs, id: Identifier, content: BufferedReader, resourceCreator: RawZipResourceCreator, dispatcher: CommandDispatcher<ServerCommandSource>)
        fun validate(args: DatapackBuildArgs, id: Identifier, content: BufferedReader, dispatcher: CommandDispatcher<ServerCommandSource>)
    }
}