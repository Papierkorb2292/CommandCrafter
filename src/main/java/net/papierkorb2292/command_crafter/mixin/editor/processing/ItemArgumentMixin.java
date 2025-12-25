package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemParser;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ItemArgument.class)
public class ItemArgumentMixin implements AnalyzingCommandNode {

    @Shadow @Final private ItemParser parser;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> stringReader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        try {
            ((AnalyzingResultDataContainer) parser).command_crafter$setAnalyzingResult(result);
            ((AllowMalformedContainer) parser).command_crafter$setAllowMalformed(true);
            ((ItemParserAccessor) parser).callParse(stringReader);
        } finally {
            ((AnalyzingResultDataContainer) parser).command_crafter$setAnalyzingResult(null);
            ((AllowMalformedContainer) parser).command_crafter$setAllowMalformed(false);
        }
    }

    @WrapMethod(
            method = "listSuggestions"
    )
    private <S> CompletableFuture<Suggestions> command_crafter$allowMalformedSuggestions(CommandContext<S> context, SuggestionsBuilder builder, Operation<CompletableFuture<Suggestions>> op) {
        if(getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) == null)
            // Don't change suggestions for vanilla
            return op.call(context, builder);

        try {
            ((AllowMalformedContainer) parser).command_crafter$setAllowMalformed(true);
            return op.call(context, builder);
        } finally {
            ((AllowMalformedContainer) parser).command_crafter$setAllowMalformed(false);
        }
    }
}
