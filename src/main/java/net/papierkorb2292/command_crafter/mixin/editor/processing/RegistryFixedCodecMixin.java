package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.papierkorb2292.command_crafter.editor.processing.codecmod.CodecTransformers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistryFixedCodec.class)
public class RegistryFixedCodecMixin<E> {

    @Shadow @Final private ResourceKey<? extends Registry<E>> registryKey;

    @ModifyExpressionValue(
            method = "decode",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/resources/Identifier;CODEC:Lcom/mojang/serialization/Codec;"
            )
    )
    private Codec<?> command_crafter$addRegistryIdSuggestions(Codec<?> identifierCodec) {
        return CodecTransformers.addResourceKeySuggestions(identifierCodec, registryKey);
    }
}
