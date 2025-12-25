package net.papierkorb2292.command_crafter.mixin.packrat;

import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.Dictionary;
import net.minecraft.util.parsing.packrat.Atom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(Dictionary.class)
public interface DictionaryAccessor<S> {
    @Accessor
    Map<Atom<?>, ? extends NamedRule<S, ?>> getTerms();
}
