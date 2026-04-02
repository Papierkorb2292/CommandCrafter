package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(RegistryFileCodec.class)
public class RegistryFileCodecMixin<E> {

    @Shadow
    @Final
    private ResourceKey<? extends Registry<E>> registryKey;

    @Shadow @Final private Codec<E> elementCodec;

    @ModifyExpressionValue(
            method = "decode",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/resources/Identifier;CODEC:Lcom/mojang/serialization/Codec;",
                    remap = true
            ),
            remap = false
    )
    private Codec<?> command_crafter$addRegistryIdSuggestions(Codec<?> identifierCodec) {
        return CodecSuggestionWrapper.Companion.simple(identifierCodec, new CodecSuggestionWrapper.SuggestionsProvider() {
            @NotNull
            @Override
            public <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                var owner = ((RegistryOps<?>)ops).owner(registryKey);
                if(owner.isEmpty()) return Stream.empty();
                if(owner.get() instanceof HolderLookup<?> wrapper) {
                    return wrapper.listElementIds().map(key -> ops.createString(key.identifier().toString()));
                }
                return Stream.empty();
            }
        });
    }

    @Inject(
            method = "decode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/HolderGetter;get(Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
                    remap = true
            ),
            remap = false
    )
    private <T> void command_crafter$suggestEntryCodecWhenIdWasFound(DynamicOps<T> ops, T input, CallbackInfoReturnable<DataResult<Pair<Holder<E>, T>>> cir) {
        final var extraBehavior = ExtraDecoderBehavior.Companion.getCurrentBehavior(ops);
        if(extraBehavior != null && !extraBehavior.getBranchBehavior().isShortCircuit())
            elementCodec.decode(ops, input);
    }
}

