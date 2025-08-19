package net.papierkorb2292.command_crafter.mixin.client.editor;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Language;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import net.papierkorb2292.command_crafter.editor.processing.PrimitiveCodecSuggestionWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.stream.Stream;

// Only applied clientside, because servers load languages differently
@Mixin(TranslatableTextContent.class)
public class TranslatableTextContentMixin {
    @ModifyReceiver(
            method = "method_54237",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;fieldOf(Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=translate"
                    )
            ),
            remap = false
    )
    private static PrimitiveCodec<String> command_crafter$suggestTranslationNames(PrimitiveCodec<String> codec, String s) {
        return new PrimitiveCodecSuggestionWrapper<>(codec, new CodecSuggestionWrapper.SuggestionsProvider() {
            @Override
            public @NotNull <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                if(Language.getInstance() instanceof TranslationStorageAccessor translationStorage) {
                    return translationStorage.getTranslations()
                            .keySet()
                            .stream()
                            .map(ops::createString);
                }
                return Stream.empty();
            }
        });
    }
}
