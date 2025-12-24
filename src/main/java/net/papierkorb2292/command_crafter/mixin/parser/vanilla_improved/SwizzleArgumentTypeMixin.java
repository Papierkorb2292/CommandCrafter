package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.command.argument.SwizzleArgumentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SwizzleArgumentType.class)
public class SwizzleArgumentTypeMixin {

    @ModifyExpressionValue(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Ljava/util/EnumSet;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C"
            ),
            allow = 1
    )
    private char command_crafter$endOnNewline(char original) {
        return original == '\n' ? ' ' : original;
    }
}
