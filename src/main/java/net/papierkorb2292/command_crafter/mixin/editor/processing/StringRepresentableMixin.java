package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.StringRepresentable;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringIdentifiableNameTransformerConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(StringRepresentable.class)
public interface StringRepresentableMixin {
    @SuppressWarnings("deprecation")
    @ModifyReturnValue(
            method = "fromEnumWithMapping(Ljava/util/function/Supplier;Ljava/util/function/Function;)Lnet/minecraft/util/StringRepresentable$EnumCodec;",
            at = @At("RETURN")
    )
    private static <E extends Enum<E> & StringRepresentable> StringRepresentable.EnumCodec<E> command_crafter$addCodecNameTransformer(StringRepresentable.EnumCodec<E> original, Supplier<E[]> enumValues, Function<String, String> valueNameTransformer) {
        ((StringIdentifiableNameTransformerConsumer)original).command_crafter$setNameTransformer(valueNameTransformer::apply);
        return original;
    }
}
