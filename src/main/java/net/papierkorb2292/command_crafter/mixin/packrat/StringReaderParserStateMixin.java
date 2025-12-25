package net.papierkorb2292.command_crafter.mixin.packrat;

import net.minecraft.util.parsing.packrat.commands.StringReaderParserState;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StringReaderParserState.class)
public abstract class StringReaderParserStateMixin {
    @Shadow public abstract int mark();

    @Inject(
            method = "restore",
            at = @At("HEAD")
    )
    private void command_crafter$storeFurthestAnalyzingResult(int cursor, CallbackInfo ci) {
        var oldCursor = mark();
        if(oldCursor > cursor)
            PackratParserAdditionalArgs.INSTANCE.storeFurthestAnalyzingResult(oldCursor);
    }
}
