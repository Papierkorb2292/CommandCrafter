package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.repository.KnownPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

@Mixin(RegistrySynchronization.class)
public interface RegistrySynchronizationAccessor {

    @Invoker
    static <T> void callPackRegistry(
            DynamicOps<Tag> nbtOps,
            RegistryDataLoader.RegistryData<T> entry,
            RegistryAccess registryManager,
            Set<KnownPack> knownPacks,
            BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> callback
    ) {
        throw new AssertionError();
    }
}
