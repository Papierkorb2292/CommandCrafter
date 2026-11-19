package net.papierkorb2292.command_crafter.parser

import com.google.gson.stream.JsonWriter
import com.mojang.brigadier.StringReader
import com.mojang.datafixers.util.Either
import com.mojang.serialization.JsonOps
import net.minecraft.commands.ParseContext
import net.minecraft.core.registries.Registries
import net.minecraft.data.DataProvider
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.resources.RegistryOps
import net.minecraft.util.GsonHelper
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage.Companion.parseWithCallbackOverride
import java.io.BufferedReader
import java.io.StringWriter

class JsonZipCreatorProcessor<T: Any>(val registry: RegistryDataLoader.RegistryData<T>) : RawZipResourceCreator.DataTypeProcessor {
    override val type: String
        get() = Registries.elementsDirPath(registry.key)

    override fun shouldProcess(args: DatapackBuildArgs) = true

    override fun process(
        args: DatapackBuildArgs,
        id: Identifier,
        content: BufferedReader,
        resourceCreator: RawZipResourceCreator,
    ) {
        // Get the registries from the parser provider
        resourceCreator.parserProvider.create(resourceCreator.functionId).parseWithCallbackOverride(StringReader("")) { _, context ->
            val decoded = decode(content, context, id)
            val resource = encode(decoded, context, id)
            resourceCreator.addResource(id, resource)
        }
    }

    override fun validate(
        args: DatapackBuildArgs,
        id: Identifier,
        content: BufferedReader,
        resourceCreator: RawZipResourceCreator,
    ) {
        resourceCreator.parserProvider.create(resourceCreator.functionId).parseWithCallbackOverride(StringReader("")) { _, context ->
            decode(content, context, id)
        }
    }

    private fun decode(content: BufferedReader, parseContext: ParseContext, id: Identifier): T {
        val json = GsonHelper.parse(content)
        return registry.elementCodec.parse(RegistryOps.create(JsonOps.INSTANCE, parseContext.holderGetters()), json).getOrThrow {
            IllegalArgumentException("Error reading registry entry $id in ${registry.key.identifier()}: $it")
        }
    }

    private fun encode(resource: T, parseContext: ParseContext, id: Identifier): RawResource {
        val json = registry.elementCodec.encodeStart(RegistryOps.create(JsonOps.INSTANCE, parseContext.holderGetters()), resource).getOrThrow {
            IllegalArgumentException("Error writing registry entry $id in ${registry.key.identifier()}: $it")
        }
        val stringWriter = StringWriter()
        val jsonWriter = JsonWriter(stringWriter)
        GsonHelper.writeValue(jsonWriter, json, DataProvider.KEY_COMPARATOR)
        return RawResource(RawResource.RawResourceType(type, "json")).apply {
            this.id = id
            this.content += Either.left(stringWriter.toString())
        }
    }
}