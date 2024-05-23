package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import net.minecraft.command.argument.packrat.ParsingState;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ParsingState.class)
public class PackratStateMixin<T> {

    @ModifyExpressionValue(
            method = "putCache",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/util/Optional;I)Lnet/minecraft/command/argument/packrat/ParsingState$PackratCache;"
            )
    )
    private ParsingState.PackratCache<T> command_crafter$cacheAnalyzingResult(ParsingState.PackratCache<T> cache) {
        ((AnalyzingResultDataContainer)(Object)cache).command_crafter$setAnalyzingResult(getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult()));
        return cache;
    }

    @ModifyReceiver(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/packrat/ParsingState$PackratCache;mark()I"
            )
    )
    private ParsingState.PackratCache<T> command_crafter$applyCachedAnalyzingResult(ParsingState.PackratCache<T> cache) {
        var cachedAnalyzingResult = ((AnalyzingResultDataContainer)(Object)cache).command_crafter$getAnalyzingResult();
        if(cachedAnalyzingResult != null) {
            PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(cachedAnalyzingResult);
        }
        return cache;
    }
}
