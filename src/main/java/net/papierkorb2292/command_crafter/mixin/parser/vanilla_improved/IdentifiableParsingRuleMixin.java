package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.packrat.IdentifiableParsingRule;
import net.minecraft.command.argument.packrat.ParsingState;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(IdentifiableParsingRule.class)
public class IdentifiableParsingRuleMixin<C, V> {

    @Inject(
            method = "parse(Lnet/minecraft/command/argument/packrat/ParsingState;)Ljava/util/Optional;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;of(Ljava/lang/Object;)Ljava/util/Optional;"
            )
    )
    private void command_crafter$unparseId(ParsingState<StringReader> state, CallbackInfoReturnable<Optional<V>> cir, @Local int start, @SuppressWarnings("OptionalUsedAsFieldOrParameterType") @Local Optional<Identifier> id) {
        var unparsingList = getOrNull(PackratParserAdditionalArgs.INSTANCE.getUnparsedArgument());
        if(unparsingList != null) {
            //noinspection OptionalGetWithoutIsPresent
            unparsingList.add(Either.left(id.get().toString()));
        }
    }
}
