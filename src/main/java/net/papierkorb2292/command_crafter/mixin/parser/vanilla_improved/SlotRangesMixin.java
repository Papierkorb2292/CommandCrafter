package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.CharPredicate;
import net.minecraft.world.inventory.SlotRanges;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SlotRanges.class)
public class SlotRangesMixin {

    @ModifyArg(
            method = { "read", "tryRead" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/ParserUtils;readWhile(Lcom/mojang/brigadier/StringReader;Lnet/minecraft/CharPredicate;)Ljava/lang/String;"
            )
    )
    private static CharPredicate command_crafter$endOnNewline(CharPredicate original) {
        return c -> original.test(c) && c != '\n';
    }
}
