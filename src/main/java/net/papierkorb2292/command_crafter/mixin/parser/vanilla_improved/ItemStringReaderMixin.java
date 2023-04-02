package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("unused")
@Mixin(ItemStringReader.class)
public class ItemStringReaderMixin {

    @Shadow @Final private StringReader reader;

    @Shadow private Either<RegistryEntry<Item>, RegistryEntryList<Item>> result;

    @Shadow @Final private boolean allowTag;

    @Shadow @Final private static SimpleCommandExceptionType TAG_DISALLOWED_EXCEPTION;

    @WrapOperation(
            method = "consume",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/ItemStringReader;readItem()V"
            )
    )
    private void command_crafter$parseInlineTag(ItemStringReader itemStringReader, Operation<Void> op) throws CommandSyntaxException {
        if(reader.canRead() && VanillaLanguage.Companion.isReaderImproved(reader) && reader.peek() == '(') {
            if(!allowTag) {
                throw TAG_DISALLOWED_EXCEPTION.createWithContext(this.reader);
            }
            result = Either.right(VanillaLanguage.Companion.parseRegistryTagTuple((DirectiveStringReader<?>) reader, Registries.ITEM));
            return;
        }
        op.call(itemStringReader);
    }
}
