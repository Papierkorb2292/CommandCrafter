package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {Term.Repeated.class, Term.RepeatedWithSeparator.class})
public class RepeatedTermsMixin<S, T> {
    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/ParseState;parse(Lnet/minecraft/util/parsing/packrat/NamedRule;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$branchAnalyzingResult(ParseState<S> instance, NamedRule<S, T> stParsingRuleEntry, Operation<T> op) {
        var branchCallback = PackratParserAdditionalArgs.INSTANCE.branchAllArgs();
        T result = null;
        try {
            result = op.call(instance, stParsingRuleEntry);
        } finally {
            branchCallback.invoke(result != null);
        }
        return result;
    }
}
