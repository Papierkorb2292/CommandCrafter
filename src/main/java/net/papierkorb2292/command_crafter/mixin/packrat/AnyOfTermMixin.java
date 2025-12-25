package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.util.parsing.packrat.Control;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Term.Alternative.class)
public class AnyOfTermMixin<S> {

    @Shadow @Final private Term<S>[] elements;

    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/Term;parse(Lnet/minecraft/util/parsing/packrat/ParseState;Lnet/minecraft/util/parsing/packrat/Scope;Lnet/minecraft/util/parsing/packrat/Control;)Z"
            )
    )
    private boolean command_crafter$branchAnalyzingResult(Term<S> term, ParseState<S> state, Scope parseResults, Control cut, Operation<Boolean> op, @Share("elementIndex") LocalIntRef elementIndex) {
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
            method = "parse",
            at = @At("RETURN")
    )
    private void command_crafter$createBranchesForRestTerms(ParseState<S> state, Scope results, Control cut, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) int startCursor, @Share("elementIndex") LocalIntRef elementIndex) {
        // Since the term was successful, the following terms are not invoked. However, PackratParserAdditionalArgs should generate branches for them,
        // for example to generate all the command completions. Thus, the following terms are invoked here before continuing.
        var originalCursor = state.mark();
        for(int i = elementIndex.get(); i < elements.length; i++) {
            state.restore(startCursor);
            var missedBranchCallback = PackratParserAdditionalArgs.INSTANCE.branchAllArgs();
            try {
                elements[i].parse(state, new Scope(), Control.UNBOUND);
            } finally {
                missedBranchCallback.invoke(false);
            }
        }
        state.restore(originalCursor);
    }
}
