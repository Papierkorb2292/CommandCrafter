package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.common.collect.BiMap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.dynamic.Codecs;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.stream.Stream;

@Mixin(Codecs.IdMapper.class)
public class CodecsIdMapperMixin<I, V> {

    @Shadow @Final private BiMap<I, V> values;

    @ModifyArg(
            method = "getCodec",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/dynamic/Codecs;idChecked(Lcom/mojang/serialization/Codec;Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            )
    )
    private Codec<I> command_crafter$addIdSuggestions(Codec<I> idCodec) {
         return new CodecSuggestionWrapper<>(idCodec, new CodecSuggestionWrapper.SuggestionsProvider() {
             @Override
             public @NotNull <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                 return values.keySet().stream().map(id -> idCodec.encodeStart(ops, id).getOrThrow());
             }
         });
    }
}
