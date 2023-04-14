package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.command.argument.ScoreHolderArgumentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScoreHolderArgumentType.class)
public class ScoreHolderArgumentTypeMixin {

    @SuppressWarnings("unused")
    @ModifyExpressionValue(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    ordinal = 1
            )
    )
    private char command_crafter$endPlayerNameOnNextLine(char c) {
        return c == '\n' ? ' ' : c;
    }
}
