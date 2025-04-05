package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(RegistryElementCodec.class)
public class RegistryElementCodecMixin<E> {

    @Shadow
    @Final
    private RegistryKey<? extends Registry<E>> registryRef;

    @Shadow @Final private Codec<E> elementCodec;

    @ModifyExpressionValue(
            method = "decode",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/util/Identifier;CODEC:Lcom/mojang/serialization/Codec;",
                    remap = true
            ),
            remap = false
    )
    private Codec<?> command_crafter$addRegistryIdSuggestions(Codec<?> identifierCodec) {
        return new CodecSuggestionWrapper<>(identifierCodec, new CodecSuggestionWrapper.SuggestionsProvider() {
            @NotNull
            @Override
            public <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                var owner = ((RegistryOps<?>)ops).getOwner(registryRef);
                if(owner.isEmpty()) return Stream.empty();
                if(owner.get() instanceof RegistryWrapper<?> wrapper) {
                    return wrapper.streamKeys().map(key -> ops.createString(key.getValue().toString()));
                }
                return Stream.empty();
            }
        });
    }

    @Inject(
            method = "decode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/registry/RegistryEntryLookup;getOptional(Lnet/minecraft/registry/RegistryKey;)Ljava/util/Optional;",
                    remap = true
            ),
            remap = false
    )
    private <T> void command_crafter$suggestEntryCodecWhenIdWasFound(DynamicOps<T> ops, T input, CallbackInfoReturnable<DataResult<Pair<RegistryEntry<E>, T>>> cir) {
        if(getOrNull(StringRangeTree.AnalyzingDynamicOps.Companion.getCURRENT_ANALYZING_OPS()) != null)
            elementCodec.decode(ops, input);
    }
}

