package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.StringIdentifiable;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringIdentifiableNameTransformerConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;
import java.util.function.Supplier;

@Mixin(StringIdentifiable.class)
public interface StringIdentifiableMixin {
    @SuppressWarnings("deprecation")
    @ModifyReturnValue(
            method = "createCodec(Ljava/util/function/Supplier;Ljava/util/function/Function;)Lnet/minecraft/util/StringIdentifiable$EnumCodec;",
            at = @At("RETURN")
    )
    private static <E extends Enum<E> & StringIdentifiable> StringIdentifiable.EnumCodec<E> command_crafter$addCodecNameTransformer(StringIdentifiable.EnumCodec<E> original, Supplier<E[]> enumValues, Function<String, String> valueNameTransformer) {
        ((StringIdentifiableNameTransformerConsumer)original).command_crafter$setNameTransformer(valueNameTransformer::apply);
        return original;
    }
}
