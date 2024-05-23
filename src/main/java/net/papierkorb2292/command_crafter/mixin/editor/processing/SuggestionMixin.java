package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.suggestion.Suggestion;
import net.papierkorb2292.command_crafter.editor.processing.helper.SuggestionReplaceEndContainer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Suggestion.class)
public class SuggestionMixin implements SuggestionReplaceEndContainer {

    private @Nullable Integer command_crafter$replaceEnd;

    @Override
    public void command_crafter$setReplaceEnd(int end) {
        command_crafter$replaceEnd = end;
    }

    @Nullable
    @Override
    public Integer command_crafter$getReplaceEnd() {
        return command_crafter$replaceEnd;
    }
}
