package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

@Mixin(SerializableRegistries.class)
public interface SerializableRegistriesAccessor {

    @Invoker
    static <T> void callSerialize(
            DynamicOps<NbtElement> nbtOps,
            RegistryLoader.Entry<T> entry,
            DynamicRegistryManager registryManager,
            Set<VersionedIdentifier> knownPacks,
            BiConsumer<RegistryKey<? extends Registry<?>>, List<SerializableRegistries.SerializedRegistryEntry>> callback
    ) {
        throw new AssertionError();
    }
}
