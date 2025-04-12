package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;
import java.util.function.Predicate;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.command.argument.ItemPredicateArgumentType$SubPredicateCheck")
public class ItemPredicateArgumentTypeSubPredicateCheckMixin {
    @ModifyReceiver(
            method = "createPredicate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Decoder;parse(Lcom/mojang/serialization/Dynamic;)Lcom/mojang/serialization/DataResult;",
                    remap = false
            )
    )
    private <T> Decoder<?> command_crafter$invokeDelayedDecodeNbtAnalyzing(Decoder<?> instance, Dynamic<T> input) {
        final var callback = getOrNull(PackratParserAdditionalArgs.INSTANCE.getDelayedDecodeNbtAnalyzeCallback());
        if(callback != null && input.getValue() instanceof NbtElement) {
            //noinspection unchecked
            callback.invoke((DynamicOps<NbtElement>)input.getOps(), instance);
        }
        return instance;
    }

    @ModifyReceiver(
            method = "createPredicate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private DataResult<Predicate<ItemStack>> command_crafter$suppressDecoderErrorsWhenAnalyzing(DataResult<Predicate<ItemStack>> original, Function<?, ?> exceptionSupplier, ImmutableStringReader reader) {
        // Replace errors with dummy predicate when analyzing, because decoder diagnostics are already generated through command_crafter$invokeDelayedDecodeNbtAnalyzing
        // This also makes the analyzer more forgiving
        return original.isError() && reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator
                ? DataResult.success(stack -> true)
                : original;
    }
}
