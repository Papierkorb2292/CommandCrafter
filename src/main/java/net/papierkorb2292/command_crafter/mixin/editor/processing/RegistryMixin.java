package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.Registry;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(Registry.class)
public interface RegistryMixin {

    @Shadow <U> Stream<U> keys(DynamicOps<U> ops);

    @ModifyExpressionValue(
            method = "getReferenceEntryCodec",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/util/Identifier;CODEC:Lcom/mojang/serialization/Codec;"
            )
    )
    private Codec<?> command_crafter$addReferenceEntryCodecSuggestions(Codec<?> identifierCodec) {
        return new CodecSuggestionWrapper<>(identifierCodec, this::keys);
    }
}
