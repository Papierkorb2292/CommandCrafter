package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.commands.arguments.item.ItemParser;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemParser.class)
public class ItemParserMixin implements AnalyzingResultDataContainer, AllowMalformedContainer {

    private AnalyzingResult command_crafter$analyzingResult = null;
    private boolean command_crafter$allowMalformed = false;

    @Override
    public void command_crafter$setAnalyzingResult(@Nullable AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Nullable
    @Override
    public AnalyzingResult command_crafter$getAnalyzingResult() {
        return command_crafter$analyzingResult;
    }

    @Override
    public void command_crafter$setAllowMalformed(boolean allowMalformed) {
        command_crafter$allowMalformed = allowMalformed;
    }

    @Override
    public boolean command_crafter$getAllowMalformed() {
        return command_crafter$allowMalformed;
    }
}
