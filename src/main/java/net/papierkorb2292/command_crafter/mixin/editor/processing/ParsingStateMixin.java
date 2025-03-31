package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.command.argument.packrat.ParsingState;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ParsingState.class)
public class ParsingStateMixin<T> {

    @ModifyReturnValue(
            method = "getCache",
            at = @At("RETURN")
    )
    private ParsingState.PackratCache<T> command_crafter$disableCacheWhenAnalyzing(ParsingState.PackratCache<T> cache) {
        // Disable cache when analyzing, such that no outdated analyzing results are used
        if(getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult()) != null)
            return null;
        return cache;
    }

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
}
