package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.common.collect.Streams;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.network.chat.TextColor;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.stream.Stream;

@Mixin(TextColor.class)
public class TextColorMixin {

    @Shadow @Final private static Map<String, TextColor> NAMED_COLORS;

    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;comapFlatMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;",
                    remap = false
            )
    )
    private static Codec<TextColor> command_crafter$addColorCodecSuggestions(Codec<TextColor> original) {
        return new CodecSuggestionWrapper<>(original, new CodecSuggestionWrapper.SuggestionsProvider() {
            @Override
            public @NotNull <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                return Streams.concat(
                        NAMED_COLORS.keySet().stream().map(ops::createString),
                        Stream.of(ops.createString("#FFFFFF"))
                );
            }
        });
    }
}
