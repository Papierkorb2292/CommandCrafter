package net.papierkorb2292.command_crafter.mixin.packrat;

import net.minecraft.util.packrat.ParsingRuleEntry;
import net.minecraft.util.packrat.ParsingRules;
import net.minecraft.util.packrat.Symbol;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ParsingRules.class)
public interface ParsingRulesAccessor<S> {
    @Accessor
    Map<Symbol<?>, ? extends ParsingRuleEntry<S, ?>> getRules();
}
