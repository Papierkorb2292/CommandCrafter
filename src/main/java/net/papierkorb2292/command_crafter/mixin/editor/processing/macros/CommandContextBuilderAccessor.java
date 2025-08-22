package net.papierkorb2292.command_crafter.mixin.editor.processing.macros;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.StringRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandContextBuilder.class)
public interface CommandContextBuilderAccessor<S> {
    @Accessor(remap = false)
    void setRange(StringRange range);
    @Accessor(remap = false)
    void setModifier(RedirectModifier<S> modifier);
    @Accessor(remap = false)
    void setForks(boolean forks);
}
