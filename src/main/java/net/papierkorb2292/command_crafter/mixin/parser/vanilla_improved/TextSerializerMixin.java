package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.StringReader;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Text.Serializer.class)
public class TextSerializerMixin {

    @Inject(
            method = "fromJson(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/text/MutableText;",
            at = @At("RETURN")
    )
    private static void command_crafter$preventSkipOfFollowingWhitespace(StringReader reader, CallbackInfoReturnable<MutableText> cir) {
        if(!reader.canRead(0) || Character.isWhitespace(reader.peek(-1))) {
            reader.setCursor(reader.getCursor() - 1);
        }
    }
}
