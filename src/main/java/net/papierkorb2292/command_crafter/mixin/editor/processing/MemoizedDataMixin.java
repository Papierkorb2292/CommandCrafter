package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.parsing.packrat.CachedParseState;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.util.parsing.packrat.CachedParseState$PositionCache")
public class MemoizedDataMixin<T> {

    @ModifyReturnValue(
            method = "getValue(I)Lnet/minecraft/util/parsing/packrat/CachedParseState$CacheEntry;",
            at = @At("RETURN")
    )
    private CachedParseState.CacheEntry<T> command_crafter$disableCacheWhenAnalyzing(CachedParseState.CacheEntry<T> original) {
        // Disable cache when analyzing, such that all data is properly added to the current analyzing result
        if(getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult()) != null)
            return null;
        return original;
    }
}
