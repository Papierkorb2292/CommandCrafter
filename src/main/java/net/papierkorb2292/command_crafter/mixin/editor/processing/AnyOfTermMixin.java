package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.command.argument.packrat.Cut;
import net.minecraft.command.argument.packrat.ParseResults;
import net.minecraft.command.argument.packrat.ParsingState;
import net.minecraft.command.argument.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(Term.AnyOfTerm.class)
public class AnyOfTermMixin<S> {

    @Shadow @Final private List<Term<S>> elements;

    @WrapOperation(
            method = "matches",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/packrat/Term;matches(Lnet/minecraft/command/argument/packrat/ParsingState;Lnet/minecraft/command/argument/packrat/ParseResults;Lnet/minecraft/command/argument/packrat/Cut;)Z"
            )
    )
    private boolean command_crafter$branchAnalyzingResult(Term<S> term, ParsingState<S> parsingState, ParseResults parseResults, Cut cut, Operation<Boolean> op, @Share("elementIndex")LocalIntRef elementIndex) {
        var index = elementIndex.get();
        elementIndex.set(elementIndex.get() + 1);
        var originalAnalyzingResult = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
        if(originalAnalyzingResult == null)
            return op.call(term, parsingState, parseResults, cut);

        var newAnalyzingResult = originalAnalyzingResult.copyExceptCompletions();
        PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(newAnalyzingResult);
        var cursor = parsingState.getCursor();
        try {
            var result = op.call(term, parsingState, parseResults, cut);
            if(!result) {
                var failedAnalyzingResult = PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().get();
                originalAnalyzingResult.combineWithCompletionProviders(failedAnalyzingResult, String.valueOf(index));
                PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(originalAnalyzingResult);
            } else {
                var parsedCursor = parsingState.getCursor();
                var successfulAnalyzingResult = PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().get();
                for(int i = index + 1; i < elements.size(); i++) {
                    parsingState.setCursor(cursor);
                    var missedCompletionAnalyzingResult = originalAnalyzingResult.copyInput();
                    PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(missedCompletionAnalyzingResult);
                    elements.get(i).matches(parsingState, new ParseResults(), Cut.NOOP);
                    successfulAnalyzingResult.combineWithCompletionProviders(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().get(), String.valueOf(i));
                }
                PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(successfulAnalyzingResult);
                parsingState.setCursor(parsedCursor);
            }
            return result;
        } catch(Exception e) {
            PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(originalAnalyzingResult);
            throw e;
        }
    }
}
