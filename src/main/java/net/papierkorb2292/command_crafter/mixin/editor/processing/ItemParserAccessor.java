package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemParser.class)
public interface ItemParserAccessor {

    @Invoker
    ItemParser.ItemResult callParse(StringReader reader) throws CommandSyntaxException;

    @Accessor
    RegistryOps<Tag> getRegistryOps();
}
