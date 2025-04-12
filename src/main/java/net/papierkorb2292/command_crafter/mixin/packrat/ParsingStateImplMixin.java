package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.util.packrat.ParsingStateImpl;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;


@Mixin(ParsingStateImpl.class)
public class ParsingStateImplMixin {

    @WrapWithCondition(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/ParsingStateImpl$MemoizedData;put(ILnet/minecraft/util/packrat/ParsingStateImpl$MemoizedValue;)V"
            )
    )
    private boolean command_crafter$disableCacheWhenUsingAdditionalArgs(@Coerce Object instance, int index, ParsingStateImpl.MemoizedValue<?> value) {
        return !PackratParserAdditionalArgs.INSTANCE.hasArgs();
    }
}
