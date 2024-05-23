package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Mixin(BlockArgumentParser.class)
public abstract class BlockArgumentParserMixin {

    @Shadow @Final private boolean allowTag;

    @Shadow @Final private StringReader reader;

    @Shadow private @Nullable RegistryEntryList<Block> tagId;

    @Shadow protected abstract void parseTagProperties() throws CommandSyntaxException;

    @Shadow private Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestions;

    @Shadow protected abstract CompletableFuture<Suggestions> suggestSnbt(SuggestionsBuilder builder);

    @Inject(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
                    remap = false,
                    ordinal = 0
            ),
            cancellable = true
    )
    private void command_crafter$parseInlineTag(CallbackInfo ci) throws CommandSyntaxException {
        if(allowTag && VanillaLanguage.Companion.isReaderInlineResources(reader) && reader.canRead() && reader.peek() == '(') {
            tagId = VanillaLanguage.Companion.parseRegistryTagTuple((DirectiveStringReader<?>)reader, Registries.BLOCK.getReadOnlyWrapper());
            if (this.reader.canRead() && this.reader.peek() == '[') {
                parseTagProperties();
                suggestions = this::suggestSnbt;
            }
            ci.cancel();
        }
    }
}
