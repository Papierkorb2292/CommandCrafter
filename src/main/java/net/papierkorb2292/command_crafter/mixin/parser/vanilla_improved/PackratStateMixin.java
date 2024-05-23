package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import net.minecraft.command.argument.packrat.ParsingState;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.helper.UnparsedArgumentContainer;
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
    private ParsingState.PackratCache<T> command_crafter$cacheUnparsedArgument(ParsingState.PackratCache<T> cache) {
        ((UnparsedArgumentContainer)(Object)cache).command_crafter$setUnparsedArgument(getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument()));
        return cache;
    }

    @ModifyReceiver(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/packrat/ParsingState$PackratCache;mark()I"
            )
    )
    private ParsingState.PackratCache<T> command_crafter$applyCachedUnparsedArgument(ParsingState.PackratCache<T> cache) {
        var cachedUnparsedArgument = ((UnparsedArgumentContainer)(Object)cache).command_crafter$getUnparsedArgument();
        if(cachedUnparsedArgument != null) {
            PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().set(cachedUnparsedArgument);
        }
        return cache;
    }
}
