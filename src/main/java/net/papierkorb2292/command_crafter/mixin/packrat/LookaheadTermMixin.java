package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.parsing.packrat.Control;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Scope;
import net.minecraft.util.parsing.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Term.LookAhead.class)
public class LookaheadTermMixin<S> {
    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/Term;parse(Lnet/minecraft/util/parsing/packrat/ParseState;Lnet/minecraft/util/parsing/packrat/Scope;Lnet/minecraft/util/parsing/packrat/Control;)Z"
            )
    )
    private boolean command_crafter$clearAdditionalArgs(Term<S> term, ParseState<S> parsingState, Scope parseResults, Control cut, Operation<Boolean> op) {
        var restoreCallback = PackratParserAdditionalArgs.INSTANCE.temporarilyClearArgs();
        try {
            return op.call(term, parsingState, parseResults, cut);
        } finally {
            restoreCallback.invoke();
        }
    }
}
