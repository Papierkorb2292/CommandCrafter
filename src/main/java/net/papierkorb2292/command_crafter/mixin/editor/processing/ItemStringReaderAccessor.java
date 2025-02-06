package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.nbt.NbtElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemStringReader.class)
public interface ItemStringReaderAccessor {

    @Invoker
    ItemStringReader.ItemResult callConsume(StringReader reader) throws CommandSyntaxException;

    @Accessor
    DynamicOps<NbtElement> getNbtOps();
}
