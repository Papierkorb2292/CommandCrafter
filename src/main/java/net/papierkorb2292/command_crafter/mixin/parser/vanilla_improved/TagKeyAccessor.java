package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagKey.class)
public interface TagKeyAccessor {

    @Accessor
    void setId(Identifier id);
}
