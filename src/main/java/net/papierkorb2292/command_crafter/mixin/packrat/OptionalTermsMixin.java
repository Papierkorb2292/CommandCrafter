package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.packrat.Cut;
import net.minecraft.util.packrat.ParseResults;
import net.minecraft.util.packrat.ParsingState;
import net.minecraft.util.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {Term.OptionalTerm.class, Term.RepeatWithSeparatorTerm.class})
public class OptionalTermsMixin<S> {
    @WrapOperation(
            method = "matches",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/Term;matches(Lnet/minecraft/util/packrat/ParsingState;Lnet/minecraft/util/packrat/ParseResults;Lnet/minecraft/util/packrat/Cut;)Z"
            )
    )
    private boolean command_crafter$branchAnalyzingResult(Term<S> term, ParsingState<S> parsingState, ParseResults parseResults, Cut cut, Operation<Boolean> op) {
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
