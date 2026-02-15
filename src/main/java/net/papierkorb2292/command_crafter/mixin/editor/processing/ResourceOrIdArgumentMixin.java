package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ResourceOrIdArgument.class)
public class ResourceOrIdArgumentMixin {
    @WrapMethod(method = "listSuggestions")
    private CompletableFuture<Suggestions> command_crafter$skipVanillaSuggestions(CommandContext<?> context, SuggestionsBuilder suggestionsBuilder, Operation<CompletableFuture<Suggestions>> op) {
        if(getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT()) != null)
            return suggestionsBuilder.buildFuture();
        return op.call(context, suggestionsBuilder);
    }
}