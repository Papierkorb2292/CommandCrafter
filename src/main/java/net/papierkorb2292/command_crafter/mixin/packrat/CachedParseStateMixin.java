package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.util.parsing.packrat.CachedParseState;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;


@Mixin(CachedParseState.class)
public class CachedParseStateMixin {

    @WrapWithCondition(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/CachedParseState$PositionCache;setValue(ILnet/minecraft/util/parsing/packrat/CachedParseState$CacheEntry;)V"
            )
    )
    private boolean command_crafter$disableCacheWhenUsingAdditionalArgs(@Coerce Object instance, int index, CachedParseState.CacheEntry<?> value) {
        return !PackratParserAdditionalArgs.INSTANCE.hasArgs();
    }
}
