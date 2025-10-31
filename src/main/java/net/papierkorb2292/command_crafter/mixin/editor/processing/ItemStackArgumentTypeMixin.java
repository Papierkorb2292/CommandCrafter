package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.command.argument.ItemStringReader;
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

@Mixin(ItemStackArgumentType.class)
public class ItemStackArgumentTypeMixin implements AnalyzingCommandNode {

    @Shadow @Final private ItemStringReader reader;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> stringReader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        try {
            ((AnalyzingResultDataContainer) reader).command_crafter$setAnalyzingResult(result);
            ((AllowMalformedContainer) reader).command_crafter$setAllowMalformed(true);
            ((ItemStringReaderAccessor) reader).callConsume(stringReader);
        } finally {
            ((AnalyzingResultDataContainer) reader).command_crafter$setAnalyzingResult(null);
            ((AllowMalformedContainer) reader).command_crafter$setAllowMalformed(false);
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
            ((AllowMalformedContainer) reader).command_crafter$setAllowMalformed(true);
            return op.call(context, builder);
        } finally {
            ((AllowMalformedContainer) reader).command_crafter$setAllowMalformed(false);
        }
    }
}
