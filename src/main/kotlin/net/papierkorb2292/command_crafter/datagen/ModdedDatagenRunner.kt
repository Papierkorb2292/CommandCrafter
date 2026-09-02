package net.papierkorb2292.command_crafter.datagen

import com.google.gson.FormattingStyle
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.stream.JsonWriter
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.synchronization.ArgumentUtils
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.data.DataProvider
import net.minecraft.resources.Identifier
import net.minecraft.util.GsonHelper
import net.minecraft.util.Util
import net.minecraft.world.level.block.Block
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.jvm.optionals.getOrNull

object ModdedDatagenRunner {

    fun exportToDirectory(dispatcher: CommandDispatcher<*>, registries: RegistryAccess, outputDirectory: Path, includeVanilla: Boolean, additionalRegistries: Map<Identifier, Iterable<Identifier>> = emptyMap()) {
        outputDirectory.createDirectories()
        exportCommands(dispatcher, outputDirectory.resolve("commands.json"))
        exportBlocks(registries, outputDirectory.resolve("blocks.json"), includeVanilla)
        exportRegistries(registries, additionalRegistries, outputDirectory.resolve("registries.json"), includeVanilla)
    }

    fun <S : Any> exportCommands(dispatcher: CommandDispatcher<S>, outputPath: Path) {
        getJsonWriter(outputPath).use { writer ->
            val json = ArgumentUtils.serializeNodeToJson(dispatcher, dispatcher.getRoot())
            GsonHelper.writeValue(writer, json, DataProvider.KEY_COMPARATOR)
        }
    }

    fun exportRegistries(registries: RegistryAccess, additionalRegistries: Map<Identifier, Iterable<Identifier>>, outputPath: Path, includeVanilla: Boolean) {
        getJsonWriter(outputPath).use { writer ->
            val json = JsonObject()
            for(registry in registries.registries())
                addRegistryData(registry, json, includeVanilla)

            for((key, values) in additionalRegistries) {
                val entries = JsonArray()
                for(value in values) {
                    if(!includeVanilla && isVanilla(value))
                        continue
                    entries.add(value.toShortString())
                }
                if(entries.size() > 0)
                    json.add(key.toShortString(), entries)
            }
            GsonHelper.writeValue(writer, json, DataProvider.KEY_COMPARATOR)
        }
    }

    private fun addRegistryData(registry: RegistryAccess.RegistryEntry<*>, out: JsonObject, includeVanilla: Boolean) {
        // Add entries
        val entries = JsonArray()
        for(key in registry.value().keySet().sorted()) {
            if(!includeVanilla && isVanilla(key))
                continue
            entries.add(key.toShortString())
        }
        if(entries.size() > 0)
            out.add(registry.key.identifier().toShortString(), entries)

        // Add tags
        val tags = JsonArray()
        for(tag in registry.value().listTagIds()) {
            if(!includeVanilla && isVanilla(tag.location))
                continue
            tags.add(tag.location.toShortString())
        }
        if(tags.size() > 0)
            out.add("tag/" + registry.key.identifier().toShortString(), tags)
    }

    fun exportBlocks(registries: RegistryAccess, outputPath: Path, includeVanilla: Boolean) {
        getJsonWriter(outputPath).use { writer ->
            val json = JsonObject()
            addBlockData(registries.lookupOrThrow(Registries.BLOCK), json, includeVanilla)
            GsonHelper.writeValue(writer, json, DataProvider.KEY_COMPARATOR)
        }
    }

    private fun addBlockData(blocks: Registry<Block>, out: JsonObject, includeVanilla: Boolean) {
        for(block in blocks.listElements()) {
            val tuple = JsonArray() // Each tuple contains an object with all properties and an object with the default state
            val id = block.unwrapKey().getOrNull() ?: continue
            if(!includeVanilla && isVanilla(id.identifier()))
                continue

            // Build properties
            val properties = JsonObject()
            val definition = block.value().stateDefinition
            for(property in definition.properties) {
                val values = JsonArray()

                for(value in property.getPossibleValues()) {
                    values.add(Util.getPropertyName(property, value))
                }

                properties.add(property.name, values)
            }
            tuple.add(properties)

            // Build default state
            val defaultState = JsonObject()
            for(property in definition.properties) {
                defaultState.addProperty(property.name, Util.getPropertyName(property, block.value().defaultBlockState().getValue(property)))
            }
            tuple.add(defaultState)

            out.add(id.identifier().toShortString(), tuple)
        }
    }

    fun generateSpyglassConfig(outputPath: Path, relativeDatagenPath: Path) {
        val configPath = outputPath.resolve("spyglass.json")
        if(Files.exists(configPath))
            return // Don't overwrite existing config, since it might already contain data
        getJsonWriter(configPath).use { writer ->
            val json = JsonObject()

            val env = JsonObject()
            json.add("env", env)

            val customResources = JsonObject()
            env.add("mcmetaSummaryOverrides", customResources)

            val registries = JsonObject()
            customResources.add("registries", registries)
            registries.addProperty("path", relativeDatagenPath.resolve("registries.json").toString())
            registries.addProperty("replace", false)

            val blocks = JsonObject()
            customResources.add("blocks", blocks)
            blocks.addProperty("path", relativeDatagenPath.resolve("blocks.json").toString())
            blocks.addProperty("replace", false)

            val commands = JsonObject()
            customResources.add("commands", commands)
            commands.addProperty("path", relativeDatagenPath.resolve("commands.json").toString())
            commands.addProperty("replace", true)

            GsonHelper.writeValue(writer, json, DataProvider.KEY_COMPARATOR)
        }
    }

    private fun isVanilla(id: Identifier) = id.namespace == "minecraft" || id.namespace == "brigadier"

    private fun getJsonWriter(path: Path): JsonWriter =
        JsonWriter(Files.newBufferedWriter(path)).also {
            it.formattingStyle = FormattingStyle.PRETTY
        }
}