package net.papierkorb2292.command_crafter.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.ClientRegistries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryLoader;
import net.minecraft.registry.tag.TagPacketSerializer;
import net.papierkorb2292.command_crafter.client.helper.SyncedRegistriesListConsumer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ClientRegistries.class)
public class ClientRegistriesMixin implements SyncedRegistriesListConsumer {

    private List<? extends RegistryLoader.Entry<?>> command_crafter$syncedRegistriesList;
    private static ThreadLocal<Set<RegistryKey<? extends Registry<?>>>> command_crafter$syncedRegistryKeys = new ThreadLocal<>();

    @Override
    public void command_crafter$setSyncedRegistriesList(@NotNull List<? extends RegistryLoader.Entry<?>> syncedRegistriesList) {
        this.command_crafter$syncedRegistriesList = syncedRegistriesList;
    }

    @WrapOperation(
            method = "createRegistryManager(Lnet/minecraft/resource/ResourceFactory;Lnet/minecraft/client/network/ClientRegistries$DynamicRegistries;Z)Lnet/minecraft/registry/DynamicRegistryManager;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientRegistries$Tags;forEach(Ljava/util/function/BiConsumer;)V"
            )
    )
    private void command_crafter$createSyncedRegistryKeysSet(@Coerce Object instance, BiConsumer<? super RegistryKey<? extends Registry<?>>, ? super TagPacketSerializer.Serialized> consumer, Operation<Void> original) {
        if(command_crafter$syncedRegistriesList == null) {
            original.call(instance, consumer);
            return;
        }
        command_crafter$syncedRegistryKeys.set(command_crafter$syncedRegistriesList.stream().map(RegistryLoader.Entry::key).collect(Collectors.toUnmodifiableSet()));
        try {
            original.call(instance, consumer);
        } finally {
            command_crafter$syncedRegistryKeys = null;
        }
    }

    @ModifyExpressionValue(
            method = "method_62159",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/registry/SerializableRegistries;isSynced(Lnet/minecraft/registry/RegistryKey;)Z",
                    remap = true
            ),
            remap = false
    )
    private static boolean command_crafter$checkCustomSyncedRegistriesList(boolean original, Map<?, ?> map, boolean local, List<?> list, DynamicRegistryManager.Immutable immutable, RegistryKey<? extends Registry<?>> registryRef) {
        final var syncedRegistryKeys = getOrNull(command_crafter$syncedRegistryKeys);
        return syncedRegistryKeys != null ? syncedRegistryKeys.contains(registryRef) : original;
    }

    @ModifyExpressionValue(
            method = "createRegistryManager(Lnet/minecraft/resource/ResourceFactory;Lnet/minecraft/client/network/ClientRegistries$DynamicRegistries;Z)Lnet/minecraft/registry/DynamicRegistryManager;",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/registry/RegistryLoader;SYNCED_REGISTRIES:Ljava/util/List;"
            )
    )
    private List<RegistryLoader.Entry<?>> command_crafter$passSyncedRegistryListToRegistryLoader(List<RegistryLoader.Entry<?>> original) {
        //noinspection unchecked
        return command_crafter$syncedRegistriesList != null ? (List<RegistryLoader.Entry<?>>) command_crafter$syncedRegistriesList : original;
    }
}
