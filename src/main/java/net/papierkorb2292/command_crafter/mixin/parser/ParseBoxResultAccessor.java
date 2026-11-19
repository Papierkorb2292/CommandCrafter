package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.ParseBox;
import net.minecraft.commands.ParseContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ParseBox.Result.class)
public interface ParseBoxResultAccessor {
    @Accessor
    ParseResults<ParseContext> getParseResults();
}
