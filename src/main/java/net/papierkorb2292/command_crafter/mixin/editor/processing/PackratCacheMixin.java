package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.command.argument.packrat.ParsingState;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParsingState.PackratCache.class)
public class PackratCacheMixin implements AnalyzingResultDataContainer {

    private AnalyzingResult command_crafter$analyzingResult = null;

    @Override
    public void command_crafter$setAnalyzingResult(@Nullable AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Nullable
    @Override
    public AnalyzingResult command_crafter$getAnalyzingResult() {
        return command_crafter$analyzingResult;
    }
}
