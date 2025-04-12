package net.papierkorb2292.command_crafter.mixin.packrat;

import net.minecraft.util.packrat.ReaderBackedParsingState;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ReaderBackedParsingState.class)
public abstract class ReaderBackedParsingStateMixin {
    @Shadow public abstract int getCursor();

    @Inject(
            method = "setCursor",
            at = @At("HEAD")
    )
    private void command_crafter$storeFurthestAnalyzingResult(int cursor, CallbackInfo ci) {
        var oldCursor = getCursor();
        if(oldCursor > cursor)
            PackratParserAdditionalArgs.INSTANCE.storeFurthestAnalyzingResult(oldCursor);
    }
}
