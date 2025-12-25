package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.StringReader;
import net.minecraft.util.parsing.packrat.Rule;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;
import net.papierkorb2292.command_crafter.parser.helper.InlineTagPackratParsingCallbacks;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Predicate;

@Mixin(targets = "net.minecraft.commands.arguments.item.ItemPredicateArgument$Context")
public class ItemPredicateArgumentTypeCallbacksMixin implements InlineTagPackratParsingCallbacks<Predicate<ItemStack>> {

    @Shadow @Final private HolderLookup.RegistryLookup<Item> items;

    @NotNull
    @Override
    public Rule<StringReader, Predicate<ItemStack>> command_crafter$getInlineTagRule() {
        return new VanillaLanguage.InlineTagRule<>(items, ItemStack::getItemHolder);
    }
}
