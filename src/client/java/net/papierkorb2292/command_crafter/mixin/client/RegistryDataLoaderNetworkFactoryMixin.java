package net.papierkorb2292.command_crafter.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.resources.NetworkRegistryLoadTask;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.papierkorb2292.command_crafter.client.NetworkServerConnection;
import net.papierkorb2292.command_crafter.client.helper.IsCustomRegistrySyncContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net/minecraft/resources/RegistryDataLoader$2")
public class RegistryDataLoaderNetworkFactoryMixin {
    private boolean command_crafter$isCustomRegistrySync;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$fetchIsCustomRegistrySync(Map<?, ?> par1, ResourceProvider par2, CallbackInfo ci) {
        var customRegistrySync = getOrNull(NetworkServerConnection.Companion.isCustomRegistrySync());
        if(customRegistrySync != null) {
            command_crafter$isCustomRegistrySync = customRegistrySync;
        }
    }

    @ModifyExpressionValue(
            method = "create",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/resources/RegistryDataLoader$RegistryData;Lcom/mojang/serialization/Lifecycle;Ljava/util/Map;Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceProvider;)Lnet/minecraft/resources/NetworkRegistryLoadTask;"
            )
    )
    private <T> NetworkRegistryLoadTask<T> command_crafter$passIsCustomRegistrySyncToTask(NetworkRegistryLoadTask<T> original) {
        if(command_crafter$isCustomRegistrySync) {
            ((IsCustomRegistrySyncContainer)original).command_crafter$setIsCustomRegistrySync(true);
        }
        return original;
    }
}
