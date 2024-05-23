package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.command.argument.packrat.Cut;
import net.minecraft.command.argument.packrat.ParseResults;
import net.minecraft.command.argument.packrat.ParsingState;
import net.minecraft.command.argument.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Term.AnyOfTerm.class)
public class AnyOfTermMixin<S> {

    @WrapOperation(
            method = "matches",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/packrat/Term;matches(Lnet/minecraft/command/argument/packrat/ParsingState;Lnet/minecraft/command/argument/packrat/ParseResults;Lnet/minecraft/command/argument/packrat/Cut;)Z"
            )
    )
    private boolean command_crafter$branchAnalyzingResult(Term<S> term, ParsingState<S> parsingState, ParseResults parseResults, Cut cut, Operation<Boolean> op) {
        var originalAnalyzingResult = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
        if(originalAnalyzingResult == null)
            return op.call(term, parsingState, parseResults, cut);

        var newAnalyzingResult = originalAnalyzingResult.copy();
        PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(newAnalyzingResult);
        try {
            var result = op.call(term, parsingState, parseResults, cut);
            if(!result)
                PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(originalAnalyzingResult);
            return result;
        } catch(Exception e) {
            PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(originalAnalyzingResult);
            throw e;
        }
    }
}
