package net.papierkorb2292.command_crafter.client

import com.mojang.serialization.Lifecycle
import net.minecraft.registry.*
import net.minecraft.registry.Registry.PendingTagLoad
import net.minecraft.registry.tag.TagGroupLoader
import net.minecraft.resource.LifecycledResourceManagerImpl
import net.minecraft.resource.ResourceType
import net.minecraft.resource.VanillaDataPackProvider
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler
import java.util.stream.Stream

class LoadedClientsideRegistries(
    val combinedRegistries: CombinedDynamicRegistries<ServerDynamicRegistryType>,
    private val pendingTagLoads: List<PendingTagLoad<*>>
) {
    companion object {
        fun load(): LoadedClientsideRegistries {
            // Static registries are copied so tags don't modify the original registries
            val initialRegistries = getCopiedInitialRegistries(ServerDynamicRegistryType.createCombinedDynamicRegistries(), ServerDynamicRegistryType.STATIC)
            val resourceManager = LifecycledResourceManagerImpl(ResourceType.SERVER_DATA, listOf(VanillaDataPackProvider.createDefaultPack()))
            val pendingTagLoads = TagGroupLoader.startReload(
                resourceManager, initialRegistries.get(ServerDynamicRegistryType.STATIC)
            )
            val precedingWorldgen = initialRegistries.getPrecedingRegistryManagers(ServerDynamicRegistryType.WORLDGEN)
            val tagRegistries = TagGroupLoader.collectRegistries(precedingWorldgen, pendingTagLoads)
            val dynamicRegistries = RegistryLoader.loadFromResource(
                resourceManager,
                tagRegistries,
                NetworkServerConnectionHandler.ALL_DYNAMIC_REGISTRIES
            )
            val tagAndDynamicRegistries = Stream.concat(tagRegistries.stream(), dynamicRegistries.stream()).toList()
            val dimensionRegistries = RegistryLoader.loadFromResource(resourceManager, tagAndDynamicRegistries, RegistryLoader.DIMENSION_REGISTRIES)
            val finalRegistries = initialRegistries.with(
                ServerDynamicRegistryType.DIMENSIONS,
                dimensionRegistries
            ).with(
                ServerDynamicRegistryType.RELOADABLE,
                dynamicRegistries
            )
            val registryLoader = LoadedClientsideRegistries(
                finalRegistries,
                pendingTagLoads
            )
            registryLoader.applyTags()
            return registryLoader
        }

        fun <DynamicRegistryType> getCopiedInitialRegistries(combinedRegistries: CombinedDynamicRegistries<DynamicRegistryType>, registryType: DynamicRegistryType): CombinedDynamicRegistries<DynamicRegistryType> {
            val copiedStatic = combinedRegistries.get(registryType)
                .streamAllRegistries()
                .map { copyRegistry(it.value) }
                .toList()
            return combinedRegistries.with(
                registryType,
                DynamicRegistryManager.ImmutableImpl(copiedStatic).toImmutable()
            )
        }

        fun <T> copyRegistry(registry: Registry<T>): Registry<T> {
            val copy = SimpleRegistry(registry.key, Lifecycle.stable())
            registry.streamEntries().forEach { entry ->
                copy.add(entry.registryKey(), entry.value(), registry.getEntryInfo(entry.registryKey()).get())
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