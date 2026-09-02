package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.util.parsing.packrat.commands.ParserBasedArgument;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ParserBasedArgument.class)
public abstract class ParserBasedArgumentMixin {

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
