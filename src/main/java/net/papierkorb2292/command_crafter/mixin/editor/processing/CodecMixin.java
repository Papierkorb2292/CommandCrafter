package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.serialization.Codec;
import net.papierkorb2292.command_crafter.editor.processing.codecmod.CodecUtilKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Codec.class)
public interface CodecMixin {

    @ModifyVariable(
            method = {
                    "withAlternative(Lcom/mojang/serialization/Codec;)Lcom/mojang/serialization/Codec;",
                    "withAlternative(Lcom/mojang/serialization/Codec;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            },
            at = @At("HEAD"),
            argsOnly = true
    )
    private Codec<?> command_crafter$markAlternativeCodecNonCanonical(Codec<?> alternative) {
        return CodecUtilKt.nonCanonical(alternative);
    }
}
