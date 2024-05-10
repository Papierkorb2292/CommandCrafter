package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import net.minecraft.command.argument.packrat.NbtParsingRule;
import net.minecraft.nbt.StringNbtReader;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(NbtParsingRule.class)
public class NbtParsingRuleMixin {

    @ModifyReceiver(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseElement()Lnet/minecraft/nbt/NbtElement;"
            )
    )
    private StringNbtReader command_crafter$analyzeNbt(StringNbtReader stringNbtReader) {
        var analyzingResult = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
        if(analyzingResult != null)
            ((AnalyzingResultCreator) stringNbtReader).command_crafter$setAnalyzingResult(analyzingResult);
        return stringNbtReader;
    }
}
