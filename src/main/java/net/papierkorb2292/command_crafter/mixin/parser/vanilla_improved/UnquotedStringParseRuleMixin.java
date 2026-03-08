package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.util.Either;
import net.minecraft.util.parsing.packrat.commands.UnquotedStringParseRule;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(UnquotedStringParseRule.class)
public class UnquotedStringParseRuleMixin {
    @ModifyReturnValue(
            method = "parse(Lnet/minecraft/util/parsing/packrat/ParseState;)Ljava/lang/String;",
            at = @At("TAIL")
    )
    private String command_crafter$unparseString(String string) {
        var unparsingListArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument());
        if(unparsingListArg != null) {
            unparsingListArg.getStringified().add(Either.left(string));
        }
        return string;
    }
}
