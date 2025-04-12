package net.papierkorb2292.command_crafter.mixin.packrat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.packrat.*;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {Term.RepeatedTerm.class, Term.RepeatWithSeparatorTerm.class})
public class RepeatedTermsMixin<S, T> {
    @WrapOperation(
            method = "matches",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/ParsingState;parse(Lnet/minecraft/util/packrat/ParsingRuleEntry;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$branchAnalyzingResult(ParsingState<S> instance, ParsingRuleEntry<S, T> stParsingRuleEntry, Operation<T> op) {
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
