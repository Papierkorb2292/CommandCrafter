package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.util.packrat.ParsingStateImpl;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.util.packrat.ParsingStateImpl$MemoizedData")
public class MemoizedDataMixin<T> {

    @ModifyReturnValue(
            method = "get(I)Lnet/minecraft/util/packrat/ParsingStateImpl$MemoizedValue;",
            at = @At("RETURN")
    )
    private ParsingStateImpl.MemoizedValue<T> command_crafter$disableCacheWhenAnalyzing(ParsingStateImpl.MemoizedValue<T> original) {
        // Disable cache when analyzing, such that all data is properly added to the current analyzing result
        if(getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult()) != null)
            return null;
        return original;
    }
}
