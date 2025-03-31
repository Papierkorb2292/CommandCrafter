package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.block.Block;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntryList;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Mixin(
        value = BlockArgumentParser.class,
        // Set priority to ensure analyzing tags in processing/BlockArgumentParserMixin takes precedence
        priority = 500
)
public abstract class BlockArgumentParserMixin {

    @Shadow @Final private boolean allowTag;

    @Shadow @Final private StringReader reader;

    @Shadow private @Nullable RegistryEntryList<Block> tagId;

    @Shadow protected abstract void parseTagProperties() throws CommandSyntaxException;

    @Shadow private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions;

    @Shadow protected abstract CompletableFuture<Suggestions> suggestSnbt(SuggestionsBuilder builder);

    @ModifyExpressionValue(
            method = "parse",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    remap = false
            )
    )
    private char command_crafter$recognizeInlineTag(char peek) {
        if(allowTag && VanillaLanguage.Companion.isReaderInlineResources(reader) && reader.canRead() && reader.peek() == '[')
            return '#';
        return peek;
    }

    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/BlockArgumentParser;parseTagId()V"
            )
    )
    private void command_crafter$parseInlineTag(BlockArgumentParser instance, Operation<Void> original) {
        if(allowTag && VanillaLanguage.Companion.isReaderInlineResources(reader) && reader.canRead() && reader.peek() == '[') {
            tagId = VanillaLanguage.Companion.parseRegistryTagTuple((DirectiveStringReader<?>)reader, Registries.BLOCK);
        } else {
            original.call(instance);
        }
    }
}
