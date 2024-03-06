package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemStringReader.class)
public interface ItemStringReaderAccessor {

    @Invoker("<init>")
    static ItemStringReader callInit(RegistryWrapper<Item> registryWrapper, StringReader reader, boolean allowTag) {
        throw new AssertionError();
    }

    @Invoker
    void callConsume() throws CommandSyntaxException;
}
