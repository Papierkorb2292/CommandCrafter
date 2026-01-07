package net.papierkorb2292.command_crafter.client

import com.mojang.serialization.Lifecycle
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator
import net.minecraft.advancements.Advancement
import net.minecraft.core.LayeredRegistryAccess
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.Registry.PendingTags
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.server.RegistryLayer
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader
import net.minecraft.world.item.crafting.Recipe
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler
import java.util.stream.Stream

class LoadedClientsideRegistries(
    val combinedRegistries: LayeredRegistryAccess<RegistryLayer>,
    private val pendingTagLoads: List<PendingTags<*>>
) {
    companion object {
        fun getParseableRegistries() = NetworkServerConnectionHandler.getAllDynamicRegistries() + listOf(
            RegistryDataLoader.RegistryData(Registries.ADVANCEMENT, Advancement.CODEC, false),
            RegistryDataLoader.RegistryData(Registries.RECIPE, Recipe.CODEC, false)
        )

        fun load(): LoadedClientsideRegistries {
            // Static registries are copied so tags don't modify the original registries
            val initialRegistries = getCopiedInitialRegistries(RegistryLayer.createRegistryAccess(), RegistryLayer.STATIC)
            val resourcePacks = mutableListOf<PackResources>(ServerPacksSource.createVanillaPackSource())
            ModResourcePackCreator(PackType.SERVER_DATA).loadPacks { resourcePacks += it.open() }
            MultiPackResourceManager(PackType.SERVER_DATA, resourcePacks).use { resourceManager ->
                val pendingTagLoads = TagLoader.loadTagsForExistingRegistries(
                    resourceManager, initialRegistries.getLayer(RegistryLayer.STATIC)
                )
                val precedingWorldgen = initialRegistries.getAccessForLoading(RegistryLayer.WORLDGEN)
                val tagRegistries = TagLoader.buildUpdatedLookups(precedingWorldgen, pendingTagLoads)
                val dynamicRegistries = RegistryDataLoader.load(
                    resourceManager,
                    tagRegistries,
                    getParseableRegistries()
                )
                val tagAndDynamicRegistries =
                    Stream.concat(tagRegistries.stream(), dynamicRegistries.listRegistries()).toList()
                val dimensionRegistries = RegistryDataLoader.load(
                    resourceManager,
                    tagAndDynamicRegistries,
                    RegistryDataLoader.DIMENSION_REGISTRIES
                )
                val finalRegistries = initialRegistries.replaceFrom(
                    RegistryLayer.DIMENSIONS,
                    dimensionRegistries
                ).replaceFrom(
                    RegistryLayer.RELOADABLE,
                    dynamicRegistries
                )
                val registryLoader = LoadedClientsideRegistries(
                    finalRegistries,
                    pendingTagLoads
                )
                registryLoader.applyTags()
                return registryLoader
            }
        }

        fun <DynamicRegistryType: Any> getCopiedInitialRegistries(combinedRegistries: LayeredRegistryAccess<DynamicRegistryType>, registryType: DynamicRegistryType): LayeredRegistryAccess<DynamicRegistryType> {
            val copiedStatic = combinedRegistries.getLayer(registryType)
                .registries()
                .map { copyRegistry(it.value) }
                .toList()
            return combinedRegistries.replaceFrom(
                registryType,
                RegistryAccess.ImmutableRegistryAccess(copiedStatic).freeze()
            )
        }

        fun <T: Any> copyRegistry(registry: Registry<T>): Registry<T> {
            val copy = MappedRegistry(registry.key(), Lifecycle.stable())
            registry.listElements().forEach { entry ->
                copy.register(entry.key(), entry.value(), registry.registrationInfo(entry.key()).get())
            }
            copy.freeze()
            return copy
        }
    }

    fun applyTags() {
        for(it in pendingTagLoads)
            it.apply()
    }
}