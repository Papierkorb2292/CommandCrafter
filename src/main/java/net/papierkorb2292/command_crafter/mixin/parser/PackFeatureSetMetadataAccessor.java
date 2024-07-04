package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.serialization.Codec;
import net.minecraft.resource.metadata.PackFeatureSetMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PackFeatureSetMetadata.class)
public interface PackFeatureSetMetadataAccessor {
    @Accessor
    static Codec<PackFeatureSetMetadata> getCODEC() {
        throw new AssertionError();
    }
}
