package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.serialization.Codec;
import net.minecraft.resource.metadata.PackOverlaysMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PackOverlaysMetadata.class)
public interface PackOverlaysMetadataAccessor {

    @Accessor
    static Codec<PackOverlaysMetadata> getCODEC() {
        throw new AssertionError();
    }
}
