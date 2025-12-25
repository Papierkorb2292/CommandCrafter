package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.tags.TagEntry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagEntry.class)
public interface TagEntryAccessor {

    @Accessor
    void setId(Identifier id);
}