package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.papierkorb2292.command_crafter.parser.helper.InlineTagPackratParsingCallbacks;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Predicate;

@Mixin(targets = "net.minecraft.commands.arguments.item.ItemPredicateArgument$Context")
public class ItemPredicateArgumentTypeCallbacksMixin implements InlineTagPackratParsingCallbacks<Predicate<ItemStack>, Item> {
    @Shadow
    @Final
    private HolderGetter<Item> items;

    @Override
    public @NotNull HolderGetter<Item> command_crafter$getRegistry() {
        return items;
    }

    @Override
    public @NotNull ResourceKey<? extends @NotNull Registry<Item>> command_crafter$getRegistryKey() {
        return Registries.ITEM;
    }

    @Override
    public @NonNull Predicate<ItemStack> command_crafter$getPredicate(@NotNull HolderSet<Item> tag) {
        return (stack) -> tag.contains(stack.typeHolder());
    }
}
