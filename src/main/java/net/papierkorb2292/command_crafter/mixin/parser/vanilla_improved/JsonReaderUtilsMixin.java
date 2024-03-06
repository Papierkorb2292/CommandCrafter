package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import net.minecraft.util.JsonReaderUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JsonReaderUtils.class)
public class JsonReaderUtilsMixin {

    @Inject(
            method = "parse",
            at = @At("RETURN")
    )
    private static <T> void command_crafter$preventSkipOfFollowingWhitespace(StringReader reader, Codec<T> codec, CallbackInfoReturnable<T> cir) {
        if(!reader.canRead(0) || Character.isWhitespace(reader.peek(-1))) {
            reader.setCursor(reader.getCursor() - 1);
        }
    }
}
