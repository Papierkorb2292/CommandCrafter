package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.packrat.ParsingRule;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.papierkorb2292.command_crafter.parser.helper.InlineTagPackratParsingCallbacks;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;

@Mixin(targets = "net/minecraft/command/argument/ItemPredicateArgumentType$Context")
public class ItemPredicateArgumentTypeCallbacksMixin implements InlineTagPackratParsingCallbacks<Predicate<ItemStack>> {

    @NotNull
    @Override
    public ParsingRule<StringReader, Predicate<ItemStack>> command_crafter$getInlineTagRule() {
        return new VanillaLanguage.InlineTagRule<>(Registries.ITEM, ItemStack::getRegistryEntry);
    }
}
