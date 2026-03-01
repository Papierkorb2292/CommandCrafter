package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.StringRange;
import net.minecraft.tags.TagEntry;
import net.papierkorb2292.command_crafter.editor.debugger.helper.StringRangeContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TagEntry.class)
public class TagEntryMixin implements StringRangeContainer {

    private StringRange command_crafter$fileRange;

    @Nullable
    @Override
    public StringRange command_crafter$getRange() {
        return command_crafter$fileRange;
    }

    @Override
    public void command_crafter$setRange(@NotNull StringRange range) {
        command_crafter$fileRange = range;
    }
}
