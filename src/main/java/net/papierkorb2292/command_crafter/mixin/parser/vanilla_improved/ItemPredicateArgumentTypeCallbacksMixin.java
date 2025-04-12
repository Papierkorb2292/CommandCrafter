package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.StringReader;
import net.minecraft.util.packrat.ParsingRule;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.papierkorb2292.command_crafter.parser.helper.InlineTagPackratParsingCallbacks;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Predicate;

@Mixin(targets = "net/minecraft/command/argument/ItemPredicateArgumentType$Context")
public class ItemPredicateArgumentTypeCallbacksMixin implements InlineTagPackratParsingCallbacks<Predicate<ItemStack>> {

    @Shadow @Final private RegistryWrapper.Impl<Item> itemRegistryWrapper;

    @NotNull
    @Override
    public ParsingRule<StringReader, Predicate<ItemStack>> command_crafter$getInlineTagRule() {
        return new VanillaLanguage.InlineTagRule<>(itemRegistryWrapper, ItemStack::getRegistryEntry);
    }
}
