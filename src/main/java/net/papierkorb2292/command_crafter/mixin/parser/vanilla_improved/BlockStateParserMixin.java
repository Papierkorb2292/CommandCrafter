package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderSet;
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
        value = BlockStateParser.class,
        // Set priority to ensure analyzing tags in processing/BlockArgumentParserMixin takes precedence
        priority = 500
)
public abstract class BlockStateParserMixin {

    @Shadow @Final private boolean forTesting;

    @Shadow @Final private StringReader reader;

    @Shadow private @Nullable HolderSet<Block> tag;

    @Shadow protected abstract void readVagueProperties() throws CommandSyntaxException;

    @Shadow private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions;

    @Shadow protected abstract CompletableFuture<Suggestions> suggestOpenNbt(SuggestionsBuilder builder);

    @ModifyExpressionValue(
            method = "parse",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    remap = false
            )
    )
    private char command_crafter$recognizeInlineTag(char peek) {
        if(forTesting && VanillaLanguage.Companion.isReaderInlineResources(reader) && reader.canRead() && reader.peek() == '[')
            return '#';
        return peek;
    }

    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/blocks/BlockStateParser;readTag()V"
            )
    )
    private void command_crafter$parseInlineTag(BlockStateParser instance, Operation<Void> original) {
        if(forTesting && VanillaLanguage.Companion.isReaderInlineResources(reader) && reader.canRead() && reader.peek() == '[') {
            tag = VanillaLanguage.Companion.parseRegistryTagTuple((DirectiveStringReader<?>)reader, BuiltInRegistries.BLOCK);
        } else {
            original.call(instance);
        }
    }
}
