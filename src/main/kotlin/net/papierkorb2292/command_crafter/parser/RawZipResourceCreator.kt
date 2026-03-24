package net.papierkorb2292.command_crafter.parser

import com.google.common.io.Files
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.datafixers.util.Either
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.FilePackResources
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.resources.IoSupplier
import net.papierkorb2292.command_crafter.mixin.parser.PathPackResourcesAccessor
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
        private val UNKNOWN_DATAPACK_EXCEPTION = DynamicCommandExceptionType { Component.nullToEmpty("Datapack $it is of unknown type and cannot be build") }
        private val PROCESSOR_EXCEPTION = Dynamic2CommandExceptionType { id, exception -> Component.nullToEmpty("Error while processing resource '$id' for datapack build: $exception") }
        private val MISSING_PACK_META_EXCEPTION = SimpleCommandExceptionType(Component.nullToEmpty("Datapack is missing pack.mcmeta"))

        val DATA_TYPE_PROCESSORS: MutableList<DataTypeProcessor> = ArrayList()

        fun buildDatapack(pack: PackResources, args: DatapackBuildArgs, dispatcher: CommandDispatcher<SharedSuggestionProvider>, output: ZipOutputStream) {
            val resourceCreator = RawZipResourceCreator()
            val dataDirectory = PackType.SERVER_DATA.directory + '/'
            var foundPackMeta = false
            when (pack) {
                is PathPackResources -> {
                    val root = (pack as PathPackResourcesAccessor).root
                    val files = java.nio.file.Files.find(root, Int.MAX_VALUE, { _, attributes -> attributes.isRegularFile })
                    files.forEach { file ->
                        val relativePath = root.relativize(file)
                        val relativePathString = relativePath.toString()
                        val ioSupplier = IoSupplier.create(file)
                        if(!relativePath.startsWith(dataDirectory)) {
                            if(relativePathString == PackResources.PACK_META)
                                foundPackMeta = true
                            copyToOutput(ZipEntry(relativePathString), ioSupplier, output)
                            return@forEach
                        }
                        val entryPath = Path.of(dataDirectory).relativize(relativePath)
                        if(entryPath.parent == null) {
                            copyToOutput(ZipEntry(relativePathString), ioSupplier, output)
                            return@forEach
                        }
                        val namespace = entryPath.getName(0)
                        val path = namespace.relativize(entryPath)
                        processFile(
                            Identifier.fromNamespaceAndPath(namespace.toString(), path.toString().replace('\\', '/')),
                            ioSupplier,
                            resourceCreator,
                            args,
                            dispatcher
                        )
                    }
                }
                is FilePackResources -> {
                    val zipFile = (pack as ZipFileProvider).`command_crafter$getZipFile`()
                    for(entry in zipFile.entries()) {
                        val ioSupplier = IoSupplier.create(zipFile, entry)
                        val relativePath = Path.of(entry.name)
                        if(!relativePath.startsWith(dataDirectory)) {
                            if(entry.name == PackResources.PACK_META)
                                foundPackMeta = true
                            copyToOutput(entry, ioSupplier, output)
                            continue
                        }
                        val entryPath = Path.of(dataDirectory).relativize(relativePath)
                        if(entryPath.parent == null) {
                            copyToOutput(entry, ioSupplier, output)
                            continue
                        }
                        val namespace = entryPath.getName(0)
                        val path = namespace.relativize(entryPath)
                        processFile(
                            Identifier.fromNamespaceAndPath(namespace.toString(), path.toString().replace('\\', '/')),
                            ioSupplier,
                            resourceCreator,
                            args,
                            dispatcher
                        )
                    }
                }
                else -> throw UNKNOWN_DATAPACK_EXCEPTION.create(pack.packId())
            }

            if(!foundPackMeta)
                throw MISSING_PACK_META_EXCEPTION.create()

            resourceCreator.createResources(output)
        }

        private fun copyToOutput(
            entry: ZipEntry,
            content: IoSupplier<InputStream>,
            output: ZipOutputStream,
        ) {
            output.putNextEntry(entry)
            val inputStream = content.get()
            inputStream.copyTo(output)
            inputStream.close()
        }

        private fun processFile(
            fileId: Identifier,
            content: IoSupplier<InputStream>,
            resourceCreator: RawZipResourceCreator,
            args: DatapackBuildArgs,
            dispatcher: CommandDispatcher<SharedSuggestionProvider>,
        ) {
            val resourceExtension = Files.getFileExtension(fileId.path)
            val resourceId = Identifier.fromNamespaceAndPath(fileId.namespace, fileId.path.substring(0, fileId.path.length - resourceExtension.length - 1))
            for(processor in DATA_TYPE_PROCESSORS) {
                if (resourceId.path.startsWith(processor.type)) {
                    val input = content.get()
                    val reader = input.bufferedReader()
                    val path = Path.of(resourceId.path)
                    val id = Identifier.fromNamespaceAndPath(resourceId.namespace, Path.of(processor.type).relativize(path).toString().replace('\\', '/'))
                    try {
                        if (processor.shouldProcess(args)) {
                            processor.process(args, id, reader, resourceCreator, dispatcher)
                            reader.close()
                            return
                        }
                        processor.validate(args, id, reader, dispatcher)
                        break
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
        resource.id = currentId
        resource.content.map { either ->
            either.map({ it }, content@{
                it.id?.run { return@content toString() }
                val childId = Identifier.fromNamespaceAndPath(parentFunctionId.namespace, "${parentFunctionId.path}--${numberSubResource(subResourceNumbering, it.type.prefix)}--craftergen")
                createResource(childId, parentFunctionId, it, zipOutput, subResourceNumbering)
                childId.toString()
            })
        }.apply {
            zipOutput.putNextEntry(ZipEntry("${Path.of("${PackType.SERVER_DATA.directory}/${currentId.namespace}").resolve(resource.type.prefix).resolve(currentId.path)}.${resource.type.fileExtension}"))
            zipOutput.writer(Charset.defaultCharset()).append(*toTypedArray()).flush()
        }
    }

    interface DataTypeProcessor {
        val type: String
        fun shouldProcess(args: DatapackBuildArgs): Boolean
        fun process(args: DatapackBuildArgs, id: Identifier, content: BufferedReader, resourceCreator: RawZipResourceCreator, dispatcher: CommandDispatcher<SharedSuggestionProvider>)
        fun validate(args: DatapackBuildArgs, id: Identifier, content: BufferedReader, dispatcher: CommandDispatcher<SharedSuggestionProvider>)
    }
}