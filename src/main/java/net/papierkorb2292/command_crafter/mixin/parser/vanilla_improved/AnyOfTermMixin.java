package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.command.argument.packrat.Cut;
import net.minecraft.command.argument.packrat.ParseResults;
import net.minecraft.command.argument.packrat.ParsingState;
import net.minecraft.command.argument.packrat.Term;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

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
        var originalUnparsingList = getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument());
        if(originalUnparsingList == null)
            return op.call(term, parsingState, parseResults, cut);

        var newUnparsingList = new ArrayList<>(originalUnparsingList);
        PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().set(newUnparsingList);
        try {
            var result = op.call(term, parsingState, parseResults, cut);
            if(!result)
                PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().set(originalUnparsingList);
            return result;
        } catch(Exception e) {
            PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument().set(originalUnparsingList);
            throw e;
        }
    }
}
