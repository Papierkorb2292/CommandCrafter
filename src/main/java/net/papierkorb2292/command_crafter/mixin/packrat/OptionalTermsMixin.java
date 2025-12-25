package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.parsing.packrat.Control;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {Term.Maybe.class, Term.RepeatedWithSeparator.class})
public class OptionalTermsMixin<S> {
    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/Term;parse(Lnet/minecraft/util/parsing/packrat/ParseState;Lnet/minecraft/util/parsing/packrat/Scope;Lnet/minecraft/util/parsing/packrat/Control;)Z"
            )
    )
    private boolean command_crafter$branchAnalyzingResult(Term<S> term, ParseState<S> parsingState, Scope parseResults, Control cut, Operation<Boolean> op) {
        var branchCallback = PackratParserAdditionalArgs.INSTANCE.branchAllArgs();
        boolean result = false;
        try {
            result = op.call(term, parsingState, parseResults, cut);
        } finally {
            branchCallback.invoke(result);
        }
        return result;
    }
}
