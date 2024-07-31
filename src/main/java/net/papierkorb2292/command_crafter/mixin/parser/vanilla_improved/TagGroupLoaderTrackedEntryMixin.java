package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.parser.helper.InlineTagFunctionIdContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TagGroupLoader.TrackedEntry.class)
public class TagGroupLoaderTrackedEntryMixin implements InlineTagFunctionIdContainer {

    private Identifier command_crafter$inlineTagFunctionId;

    @Nullable
    @Override
    public Identifier command_crafter$getInlineTagFunctionId() {
        return command_crafter$inlineTagFunctionId;
    }

    @Override
    public void command_crafter$setInlineTagFunctionId(@NotNull Identifier id) {
        command_crafter$inlineTagFunctionId = id;
    }
}
