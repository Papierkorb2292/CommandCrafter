package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.EitherCodec;
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EitherCodec.class)
public class EitherCodecMixin<F, S> {

    @Shadow(remap = false) @Final private Codec<S> second;

    @Inject(
            method = "decode",
            at = @At(
                    value = "RETURN",
                    ordinal = 0
            ),
            remap = false
    )
    private <T> void command_crafter$suggestSecondCodecWhenFirstWasSuccessful(DynamicOps<T> ops, T input, CallbackInfoReturnable<DataResult<Pair<Either<F, S>, T>>> cir) {
        final var extraBehavior = ExtraDecoderBehavior.Companion.getCurrentBehavior(ops);
        if(extraBehavior != null && extraBehavior.getBranchBehavior() != ExtraDecoderBehavior.BranchBehavior.SHORT_CIRCUIT)
            second.decode(ops, input);
    }
}
