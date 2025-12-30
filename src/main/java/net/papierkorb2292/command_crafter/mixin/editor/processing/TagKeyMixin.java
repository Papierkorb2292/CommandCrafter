package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(TagKey.class)
public class TagKeyMixin {

    @ModifyReturnValue(
            method = "codec",
            at = @At("RETURN")
    )
    private static Codec<?> command_crafter$addTagIdSuggestions(Codec<?> tagCodec, ResourceKey<? extends Registry<?>> resourceKey) {
        return new CodecSuggestionWrapper<>(tagCodec, new CodecSuggestionWrapper.SuggestionsProvider() {
            @NotNull
            @Override
            public <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                var owner = ((RegistryOps<?>)ops).owner(resourceKey);
                if(owner.isEmpty()) return Stream.empty();
                if(owner.get() instanceof HolderLookup<?> wrapper) {
                    return wrapper.listTagIds().map(key -> ops.createString(key.location().toString()));
                }
                return Stream.empty();
            }
        });
    }

    @ModifyReturnValue(
            method = "hashedCodec",
            at = @At("RETURN")
    )
    private static Codec<?> command_crafter$addHashedTagIdSuggestions(Codec<?> tagCodec, ResourceKey<? extends Registry<?>> resourceKey) {
        return new CodecSuggestionWrapper<>(tagCodec, new CodecSuggestionWrapper.SuggestionsProvider() {
            @NotNull
            @Override
            public <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                var owner = ((RegistryOps<?>)ops).owner(resourceKey);
                if(owner.isEmpty()) return Stream.empty();
                if(owner.get() instanceof HolderLookup<?> wrapper) {
                    return wrapper.listTagIds().map(key -> ops.createString('#' + key.location().toString()));
                }
                return Stream.empty();
            }
        });
    }
}
