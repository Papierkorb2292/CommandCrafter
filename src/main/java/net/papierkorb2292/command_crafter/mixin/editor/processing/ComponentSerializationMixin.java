package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.ComponentSerialization;
import net.papierkorb2292.command_crafter.editor.processing.codecmod.CodecUtilKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ComponentSerialization.class)
public class ComponentSerializationMixin {
    @ModifyExpressionValue(
            method = "createCodec",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/ExtraCodecs;nonEmptyList(Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/Codec;"
            )
    )
    private static Codec<?> command_crafter$markListCodecNonCanonical(Codec<?> codec) {
        return CodecUtilKt.nonCanonical(codec);
    }
}
