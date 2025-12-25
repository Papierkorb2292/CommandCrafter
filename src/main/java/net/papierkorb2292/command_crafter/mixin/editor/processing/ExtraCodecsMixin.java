package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.stream.Stream;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ExtraCodecs.class)
public class ExtraCodecsMixin {
    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;comapFlatMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;",
                    remap = false
            ),
            slice = @Slice(
                    to = @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/util/ExtraCodecs;TAG_OR_ELEMENT_ID:Lcom/mojang/serialization/Codec;",
                            opcode = Opcodes.PUTSTATIC
                    )
            )
    )
    private static Codec<?> command_crafter$addTagEntrySuggestions(Codec<?> identifierCodec) {
        // Suggest ids in tags
        return new CodecSuggestionWrapper<>(identifierCodec, new CodecSuggestionWrapper.SuggestionsProvider() {
            @NotNull
            @Override
            public <T> Stream<T> getSuggestions(@NotNull DynamicOps<T> ops) {
                var registry = getOrNull(StringRangeTreeJsonResourceAnalyzer.Companion.getCURRENT_TAG_ANALYZING_REGISTRY());
                if (registry == null)
                    return Stream.empty();
                return Stream.concat(
                        registry.listElementIds().map(key -> ops.createString(key.identifier().toString())),
                        registry.listTagIds().map(key -> ops.createString("#" + key.location().toString()))
                );
            }
        });
    }
}
