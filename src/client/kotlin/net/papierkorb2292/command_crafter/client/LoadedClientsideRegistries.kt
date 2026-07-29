package net.papierkorb2292.command_crafter.client

import com.mojang.serialization.Lifecycle
import net.fabricmc.fabric.impl.loot.LootUtil
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator
import net.minecraft.core.*
import net.minecraft.core.Registry.PendingTags
import net.minecraft.core.component.DataComponentInitializers
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.RegistryDataLoader
import net.minecraft.server.RegistryLayer
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.repository.ServerPacksSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.tags.TagLoader
import net.papierkorb2292.command_crafter.editor.NetworkServerConnectionHandler
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.stream.Stream

class LoadedClientsideRegistries(
    val combinedRegistries: LayeredRegistryAccess<RegistryLayer>,
    private val pendingTagLoads: List<PendingTags<*>>,
    private val pendingComponents: List<DataComponentInitializers.PendingComponents<*>>
) {
    companion object {
        fun getParseableRegistries() = NetworkServerConnectionHandler.getAllDatapackRegistries()

        fun load(executor: Executor): CompletableFuture<LoadedClientsideRegistries> {
            // Static registries are copied so tags don't modify the original registries
            val initialRegistries = getCopiedInitialRegistries(RegistryLayer.createRegistryAccess(), RegistryLayer.STATIC)
            val resourcePacks = mutableListOf<PackResources>(ServerPacksSource.createVanillaPackSource().fullResources())
            ModResourcePackCreator(PackType.SERVER_DATA).loadPacks { pack -> pack.open().forEach { resourcePacks += it } }
            return MultiPackResourceManager(PackType.SERVER_DATA, resourcePacks).use { resourceManager ->
                val pendingTagLoads = TagLoader.loadTagsForExistingRegistries(
                    resourceManager, initialRegistries.getLayer(RegistryLayer.STATIC)
                )
                val precedingWorldgen = initialRegistries.getAccessForLoading(RegistryLayer.WORLD)
                val tagRegistries = TagLoader.buildUpdatedLookups(precedingWorldgen, pendingTagLoads)
                // TODO: Maybe load reloadable registries through ReloadableServerRegistries instead?
                LootUtil.startReload(resourceManager, HolderLookup.Provider.create(tagRegistries.stream()))
                RegistryDataLoader.load(
                    resourceManager,
                    tagRegistries,
                    getParseableRegistries(),
                    executor,
                ).thenCompose { dynamicRegistries ->
                    val tagAndDynamicRegistries =
                        Stream.concat(tagRegistries.stream(), dynamicRegistries.listRegistries()).toList()
                    RegistryDataLoader.load(
                        resourceManager,
                        tagAndDynamicRegistries,
                        RegistryDataLoader.DIMENSION_REGISTRIES,
                        executor,
                    ).thenApply { dimensionRegistries ->
                        val finalRegistries = initialRegistries.replaceFrom(
                            RegistryLayer.DIMENSIONS,
                            dimensionRegistries
                        ).replaceFrom(
                            RegistryLayer.RELOADABLE,
                            dynamicRegistries
                        )
                        val pendingComponents = BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(finalRegistries.compositeAccess())
                        val registryLoader = LoadedClientsideRegistries(
                            finalRegistries,
                            pendingTagLoads,
                            pendingComponents,
                        )
                        registryLoader.applyTagsAndComponents()
                        registryLoader
                    }
                }.whenComplete { _, _ -> LootUtil.endReload(resourceManager) }
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

    fun applyTagsAndComponents() {
        for(it in pendingTagLoads)
            it.apply()
        for(it in pendingComponents)
            it.apply()
    }
}