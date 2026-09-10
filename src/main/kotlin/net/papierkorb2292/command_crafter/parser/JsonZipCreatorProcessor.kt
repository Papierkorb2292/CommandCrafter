package net.papierkorb2292.command_crafter.parser

import com.google.gson.stream.JsonWriter
import com.mojang.brigadier.CommandDispatcher
import com.mojang.datafixers.util.Either
import com.mojang.serialization.JsonOps
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.registries.Registries
import net.minecraft.data.DataProvider
import net.minecraft.resources.Identifier
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.resources.RegistryOps
import net.minecraft.util.GsonHelper
import net.papierkorb2292.command_crafter.parser.helper.RawResource
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
        dispatcher: CommandDispatcher<SharedSuggestionProvider>,
        buildContext: CommandBuildContext,
    ) {
        val decoded = decode(content, buildContext, id)
        val resource = encode(decoded, buildContext, id)
        resourceCreator.addResource(id, resource)
    }

    override fun validate(
        args: DatapackBuildArgs,
        id: Identifier,
        content: BufferedReader,
        dispatcher: CommandDispatcher<SharedSuggestionProvider>,
        buildContext: CommandBuildContext,
    ) {
        decode(content, buildContext, id)
    }

    private fun decode(content: BufferedReader, buildContext: CommandBuildContext, id: Identifier): T {
        val json = GsonHelper.parse(content)
        return registry.elementCodec.parse(RegistryOps.create(JsonOps.INSTANCE, buildContext), json).getOrThrow {
            IllegalArgumentException("Error reading registry entry $id in ${registry.key.identifier()}: $it")
        }
    }

    private fun encode(resource: T, buildContext: CommandBuildContext, id: Identifier): RawResource {
        val json = registry.elementCodec.encodeStart(RegistryOps.create(JsonOps.INSTANCE, buildContext), resource).getOrThrow {
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