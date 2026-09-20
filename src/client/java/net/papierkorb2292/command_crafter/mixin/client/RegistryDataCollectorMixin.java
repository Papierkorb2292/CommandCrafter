package net.papierkorb2292.command_crafter.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagNetworkSerialization;
import net.papierkorb2292.command_crafter.client.DummyMissingTagLookup;
import net.papierkorb2292.command_crafter.client.LoadedClientsideRegistries;
import net.papierkorb2292.command_crafter.client.NetworkServerConnection;
import net.papierkorb2292.command_crafter.client.editor.SyncedRegistriesListConsumer;
import net.papierkorb2292.command_crafter.client.helper.IsCustomRegistrySyncContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;
import static net.papierkorb2292.command_crafter.helper.UtilKt.runWithValueSwap;

@Mixin(RegistryDataCollector.class)
public class RegistryDataCollectorMixin implements SyncedRegistriesListConsumer, IsCustomRegistrySyncContainer {

    private List<? extends RegistryDataLoader.RegistryData<?>> command_crafter$syncedRegistriesList;
    private boolean command_crafter$isCustomRegistrySync;
    private static ThreadLocal<Set<ResourceKey<? extends Registry<?>>>> command_crafter$syncedRegistryKeys = new ThreadLocal<>();

    @Override
    public void command_crafter$setSyncedRegistriesList(@NotNull List<? extends RegistryDataLoader.RegistryData<?>> syncedRegistriesList) {
        this.command_crafter$syncedRegistriesList = syncedRegistriesList;
    }

    @WrapOperation(
            method = "loadNewElementsAndTags(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/client/multiplayer/RegistryDataCollector$ContentsCollector;Z)Lnet/minecraft/core/RegistryAccess;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/RegistryDataCollector$TagCollector;forEach(Ljava/util/function/BiConsumer;)V"
            )
    )
    private void command_crafter$createSyncedRegistryKeysSet(@Coerce Object instance, BiConsumer<? super ResourceKey<? extends Registry<?>>, ? super TagNetworkSerialization.NetworkPayload> consumer, Operation<Void> original) {
        if(command_crafter$syncedRegistriesList == null) {
            original.call(instance, consumer);
            return;
        }
        command_crafter$syncedRegistryKeys.set(command_crafter$syncedRegistriesList.stream().map(RegistryDataLoader.RegistryData::key).collect(Collectors.toUnmodifiableSet()));
        try {
            original.call(instance, consumer);
        } finally {
            command_crafter$syncedRegistryKeys.remove();
        }
    }

    @ModifyExpressionValue(
            method = "lambda$loadNewElementsAndTags$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/RegistrySynchronization;isNetworkable(Lnet/minecraft/resources/ResourceKey;)Z",
                    remap = true
            ),
            remap = false
    )
    private static boolean command_crafter$checkCustomSyncedRegistriesList(boolean original, Map<?, ?> map, boolean local, List<?> list, RegistryAccess.Frozen immutable, ResourceKey<? extends Registry<?>> registryRef) {
        final var syncedRegistryKeys = getOrNull(command_crafter$syncedRegistryKeys);
        return syncedRegistryKeys != null ? syncedRegistryKeys.contains(registryRef) : original;
    }

    @ModifyExpressionValue(
            method = "loadNewElementsAndTags(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/client/multiplayer/RegistryDataCollector$ContentsCollector;Z)Lnet/minecraft/core/RegistryAccess;",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/resources/RegistryDataLoader;SYNCHRONIZED_REGISTRIES:Ljava/util/List;"
            )
    )
    private List<RegistryDataLoader.RegistryData<?>> command_crafter$passSyncedRegistryListToRegistryLoader(List<RegistryDataLoader.RegistryData<?>> original) {
        //noinspection unchecked
        return command_crafter$syncedRegistriesList != null ? (List<RegistryDataLoader.RegistryData<?>>) command_crafter$syncedRegistriesList : original;
    }

    @ModifyExpressionValue(
            method = "loadNewElementsAndTags(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/client/multiplayer/RegistryDataCollector$ContentsCollector;Z)Lnet/minecraft/core/RegistryAccess;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientRegistryLayer;createRegistryAccess()Lnet/minecraft/core/LayeredRegistryAccess;"
            )
    )
    private LayeredRegistryAccess<ClientRegistryLayer> command_crafter$copyInitialRegistries(LayeredRegistryAccess<ClientRegistryLayer> original) {
        if(command_crafter$isCustomRegistrySync) {
            // Copy builtin registries to avoid modifying the original registries with tags
            return LoadedClientsideRegistries.Companion.getCopiedInitialRegistries(original, ClientRegistryLayer.STATIC);
        }
        return original;
    }

    @ModifyExpressionValue(
            method = "loadNewElementsAndTags(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/client/multiplayer/RegistryDataCollector$ContentsCollector;Z)Lnet/minecraft/core/RegistryAccess;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/tags/TagLoader;buildUpdatedLookups(Lnet/minecraft/core/RegistryAccess$Frozen;Ljava/util/List;)Ljava/util/List;"
            )
    )
    private List<HolderLookup.RegistryLookup<?>> command_crafter$provideDummyTags(List<HolderLookup.RegistryLookup<?>> original) {
        if(command_crafter$isCustomRegistrySync) {
            // Wrap with DummyMissingTagLookup such that non-existent tags are allowed and are not added to the registry
            return original.stream().map(DummyMissingTagLookup::new).collect(Collectors.toList());
        }
        return original;
    }

    @WrapOperation(
            method = "loadNewElementsAndTags",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/RegistryDataLoader;load(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceProvider;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<RegistryAccess.Frozen> command_crafter$passCustomRegistrySyncToLoader(Map<ResourceKey<? extends Registry<?>>, RegistryDataLoader.NetworkedRegistryData> entries, ResourceProvider knownDataSource, List<HolderLookup.RegistryLookup<?>> contextRegistries, List<RegistryDataLoader.RegistryData<?>> registriesToLoad, Executor executor, Operation<CompletableFuture<RegistryAccess.Frozen>> op) {
        // Pass isCustomRegstrySync to the loader, so the load task can use it
        return runWithValueSwap(
                NetworkServerConnection.Companion.isCustomRegistrySync(),
                command_crafter$isCustomRegistrySync,
                () -> op.call(entries, knownDataSource, contextRegistries, registriesToLoad, executor)
        );
    }

    @Override
    public void command_crafter$setIsCustomRegistrySync(boolean shouldCopyRegistries) {
        command_crafter$isCustomRegistrySync = shouldCopyRegistries;
    }
}
