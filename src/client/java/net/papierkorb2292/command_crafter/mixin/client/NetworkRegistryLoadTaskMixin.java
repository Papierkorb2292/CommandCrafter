package net.papierkorb2292.command_crafter.mixin.client;

import com.mojang.serialization.Lifecycle;
import net.minecraft.resources.NetworkRegistryLoadTask;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;
import net.papierkorb2292.command_crafter.client.helper.IsCustomRegistrySyncContainer;
import net.papierkorb2292.command_crafter.editor.MutableRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(NetworkRegistryLoadTask.class)
abstract public class NetworkRegistryLoadTaskMixin<T> extends RegistryLoadTask<T> implements IsCustomRegistrySyncContainer {

    private boolean command_crafter$isCustomRegistrySync;

    protected NetworkRegistryLoadTaskMixin(RegistryDataLoader.RegistryData<T> data, Lifecycle lifecycle, Map<ResourceKey<?>, Exception> loadingErrors) {
        super(data, lifecycle, loadingErrors);
    }

    @Override
    public void command_crafter$setIsCustomRegistrySync(boolean shouldCopyRegistries) {
        command_crafter$isCustomRegistrySync = shouldCopyRegistries;
    }

    @Inject(
            method = "lambda$load$2", // The lambda that calls registryTags()
            at = @At("TAIL")
    )
    private void command_crafter$deleteUnboundTagsFurCustomRegistrySync(RegistryDataLoader.NetworkedRegistryData registryEntries, List<?> pendingRegistrations, CallbackInfo ci) {
        if(command_crafter$isCustomRegistrySync) {
            // Remove unbound tags, because it is possible that a tag was present when the world was loaded,
            // and that it is used by a dynamic registry entry, but the tag might have been removed now with the
            // registry entry still there. This should now cause an error due to unbound tags.
            ((MutableRegistry)readOnlyRegistry()).command_crafter$removeUnboundTags();
        }
    }
}
