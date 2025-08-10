package net.papierkorb2292.command_crafter.mixin.client.editor;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;
import net.minecraft.text.KeybindTextContent;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import net.papierkorb2292.command_crafter.editor.processing.PrimitiveCodecSuggestionWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

// Only applied clientside, because servers don't know which keybinds there are
@Mixin(KeybindTextContent.class)
public class KeybindTextContentMixin {
    @ModifyReceiver(
            method = "method_54228",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;fieldOf(Ljava/lang/String;)Lcom/mojang/serialization/MapCodec;"
            ),
            remap = false
    )
    private static PrimitiveCodec<String> command_crafter$suggestKeybindNames(PrimitiveCodec<String> codec, String s) {
        return new PrimitiveCodecSuggestionWrapper<>(codec, new CodecSuggestionWrapper.SuggestionsProvider() {
            @Override
            public @NotNull <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                return KeyBindingAccessor.getKEYS_BY_ID().keySet().stream()
                        .map(ops::createString);
            }
        });
    }
}
