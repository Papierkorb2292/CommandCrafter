package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.command.argument.packrat.ParsingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ParsingState.class)
public interface ParsingStateAccessor {
    @Accessor
    Map<?, ParsingState.PackratCache<?>> getPackrats();
}
