package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.util.packrat.Cut;
import net.minecraft.util.packrat.ParseResults;
import net.minecraft.util.packrat.ParsingState;
import net.minecraft.util.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Term.AnyOfTerm.class)
public class AnyOfTermMixin<S> {

    @Shadow @Final private Term<S>[] elements;

    @WrapOperation(
            method = "matches",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/Term;matches(Lnet/minecraft/util/packrat/ParsingState;Lnet/minecraft/util/packrat/ParseResults;Lnet/minecraft/util/packrat/Cut;)Z"
            )
    )
    private boolean command_crafter$branchAnalyzingResult(Term<S> term, ParsingState<S> state, ParseResults parseResults, Cut cut, Operation<Boolean> op, @Share("elementIndex") LocalIntRef elementIndex) {
        elementIndex.set(elementIndex.get() + 1);

        var branchCallback = PackratParserAdditionalArgs.INSTANCE.branchAllArgs();
        boolean result = false;
        try {
            result = op.call(term, state, parseResults, cut);
        } finally {
            branchCallback.invoke(result);
        }
        return result;
    }

    @Inject(
            method = "matches",
            at = @At("RETURN")
    )
    private void command_crafter$createBranchesForRestTerms(ParsingState<S> state, ParseResults results, Cut cut, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) int startCursor, @Share("elementIndex") LocalIntRef elementIndex) {
        // Since the term was successful, the following terms are not invoked. However, PackratParserAdditionalArgs should generate branches for them,
        // for example to generate all the command completions. Thus, the following terms are invoked here before continuing.
        var originalCursor = state.getCursor();
        for(int i = elementIndex.get(); i < elements.length; i++) {
            state.setCursor(startCursor);
            var missedBranchCallback = PackratParserAdditionalArgs.INSTANCE.branchAllArgs();
            try {
                elements[i].matches(state, new ParseResults(), Cut.NOOP);
            } finally {
                missedBranchCallback.invoke(false);
            }
        }
        state.setCursor(originalCursor);
    }
}
