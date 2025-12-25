package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.core.Holder;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ResourceOrIdArgument.InlineResult.class)
public class RegistryEntryArgumentTypeDirectParserMixin<T, O> {
    @ModifyReceiver(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private <E> DataResult<T> command_crafter$suppressDecoderErrorsWhenAnalyzing(DataResult<T> original, Function<String, E> stringEFunction, ImmutableStringReader reader, @Cancellable CallbackInfoReturnable<Holder<T>> cir) {
        // Skip entries with errors when analyzing, because decoder diagnostics are already generated through command_crafter$analyze
        // This also makes the analyzer more forgiving
        if(original.isError() && reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            cir.setReturnValue(null);
        }
        return original;
    }
}
