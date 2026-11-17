package net.papierkorb2292.command_crafter.mixin.editor.processing.macros;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.StringRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandContextBuilder.class)
public interface CommandContextBuilderAccessor<S> {
    @Accessor
    void setRange(StringRange range);
    @Accessor
    void setModifier(RedirectModifier<S> modifier);
    @Accessor
    void setForks(boolean forks);
}
