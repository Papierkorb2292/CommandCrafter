package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ParserBackedArgumentType;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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

            result.addCompletionProvider(AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL, new AnalyzingResult.RangedDataProvider<>(range, cursor -> {
                var sourceCursor = reader.getCursorMapper().mapToSource(cursor, false);
                var completionProvider = parsedAnalyzingResult.getCompletionProviderForCursor(sourceCursor);
                if(completionProvider == null)
                    return CompletableFuture.completedFuture(Collections.emptyList());
                var completionFuture = completionProvider.getDataProvider().invoke(sourceCursor);
                // Make completions unique, because packrat parsing can result in duplicated completions
                return completionFuture.thenApply(completions -> new ArrayList<>(new LinkedHashSet<>(completions)));
            }), true);
        } finally {
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().remove();
            PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult().remove();
            PackratParserAdditionalArgs.INSTANCE.getFurthestAnalyzingResult().remove();
        }
    }
}
