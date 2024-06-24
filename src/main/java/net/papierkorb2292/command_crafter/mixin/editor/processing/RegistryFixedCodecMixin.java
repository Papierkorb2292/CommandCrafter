package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryFixedCodec;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(RegistryFixedCodec.class)
public class RegistryFixedCodecMixin<E> {

    @Shadow @Final private RegistryKey<? extends Registry<E>> registry;

    @ModifyExpressionValue(
            method = "decode",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/util/Identifier;CODEC:Lcom/mojang/serialization/Codec;"
            )
    )
    private Codec<?> command_crafter$addReferenceEntryCodecSuggestions(Codec<?> identifierCodec) {
        return new CodecSuggestionWrapper<>(identifierCodec, new CodecSuggestionWrapper.SuggestionsProvider() {
            @NotNull
            @Override
            public <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                var owner = ((RegistryOps<?>)ops).getOwner(registry);
                if(owner.isEmpty()) return Stream.empty();
                if(owner.get() instanceof RegistryWrapper<?> wrapper) {
                    return wrapper.streamKeys().map(key -> ops.createString(key.getValue().toString()));
                }
                return Stream.empty();
            }
        });
    }
}
