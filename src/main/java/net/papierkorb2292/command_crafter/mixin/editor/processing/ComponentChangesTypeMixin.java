package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.papierkorb2292.command_crafter.editor.processing.CodecSuggestionWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

@Mixin(targets = "net.minecraft.core.component.DataComponentPatch$PatchKey")
public class ComponentChangesTypeMixin {
    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;flatXmap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;",
                    remap = false
            )
    )
    private static Codec<Object> command_crafter$addComponentSuggestions(Codec<Object> original) {
        return new CodecSuggestionWrapper<>(original, ComponentChangesTypeMixin::command_crafter$generateComponentSuggestions);
    }

    private static <T> Stream<T> command_crafter$generateComponentSuggestions(DynamicOps<T> ops) {
        final var ids = BuiltInRegistries.DATA_COMPONENT_TYPE.keySet();
        return Stream.concat(
                ids.stream().map(id -> ops.createString("!" + id.toString())),
                ids.stream().map(id -> ops.createString(id.toString()))
        );
    }
}
