package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemPredicateArgumentType;
import net.minecraft.command.argument.packrat.ArgumentParser;
import net.minecraft.command.argument.packrat.ParseErrorList;
import net.minecraft.command.argument.packrat.ParsingStateImpl;
import net.minecraft.item.ItemStack;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

@Mixin(ItemPredicateArgumentType.class)
public abstract class ItemPredicateArgumentTypeMixin implements AnalyzingCommandNode {

    @Shadow @Final private ArgumentParser<List<Predicate<ItemStack>>> parser;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var readerCopy = reader.copy();
        readerCopy.setCursor(range.getStart());
        PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().set(result.copyInput());
        var parsingState = new ParsingStateImpl(parser.rules(), new ParseErrorList.Impl<>(), readerCopy);
        parser.startParsing(parsingState);
        result.combineWithExceptCompletions(
                ((ParsingStateAccessor) parsingState).getPackrats().values().stream()
                    .sorted(Comparator.comparing(cache -> -cache.mark()))
                    .map(cache -> ((AnalyzingResultDataContainer) (Object) cache).command_crafter$getAnalyzingResult())
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().get())
        );
        var parsedAnalyzingResult = PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().get();
        result.addCompletionProvider(AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL, new AnalyzingResult.RangedDataProvider<>(range, cursor -> {
            var completionProvider = parsedAnalyzingResult.getCompletionProviderForCursor(cursor);
            if(completionProvider == null)
                return CompletableFuture.completedFuture(Collections.emptyList());
            var completionFuture = completionProvider.getDataProvider().invoke(cursor);
            // Make completions unique, because packrat parsing can result in duplicated completions
            return completionFuture.thenApply(completions -> new ArrayList<>(new LinkedHashSet<>(completions)));
        }), true);
        PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().remove();
    }
}
