package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ParserBackedArgumentType;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ParserBackedArgumentType.class)
public abstract class ParserBackedArgumentTypeMixin<T> implements AnalyzingCommandNode {

    @Shadow public abstract T parse(StringReader reader) throws CommandSyntaxException;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(new PackratParserAdditionalArgs.AnalyzingResultBranchingArgument(result.copyInput()));
        PackratParserAdditionalArgs.INSTANCE.setupFurthestAnalyzingResultStart();
        PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().set(true);

        try {
            try {
                parse(reader);
            } catch(CommandSyntaxException ignored) {}

            var parsedAnalyzingResult = PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().get().getAnalyzingResult();
            var furthestAnalyzingResult = PackratParserAdditionalArgs.INSTANCE.getAndRemoveFurthestAnalyzingResult();
            if(furthestAnalyzingResult == null) furthestAnalyzingResult = parsedAnalyzingResult;
            result.combineWithExceptCompletions(furthestAnalyzingResult);

            result.addCompletionProviderWithContinuosMapping(AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL, new AnalyzingResult.RangedDataProvider<>(range, sourceCursor -> {
                var completionProvider = parsedAnalyzingResult.getCompletionProviderForCursor(sourceCursor);
                if(completionProvider == null)
                    return CompletableFuture.completedFuture(Collections.emptyList());
                var completionFuture = completionProvider.getDataProvider().invoke(sourceCursor);
                // Make completions unique, because packrat parsing can result in duplicated completions
                return completionFuture.thenApply(completions -> new ArrayList<>(new LinkedHashSet<>(completions)));
            }));
        } finally {
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().remove();
            PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().remove();
            PackratParserAdditionalArgs.INSTANCE.getFurthestAnalyzingResult().remove();
        }
    }

    @WrapMethod(method = "listSuggestions")
    private <S> CompletableFuture<Suggestions> command_crafter$allowMalformedSuggestions(CommandContext<S> context, SuggestionsBuilder builder, Operation<CompletableFuture<Suggestions>> op) {
        if(getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) == null)
            // Don't change suggestions for vanilla
            return op.call(context, builder);
        PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().set(true);
        try {
            return op.call(context, builder);
        } finally {
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().remove();
        }
    }
}
