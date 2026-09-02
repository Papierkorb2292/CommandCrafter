package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(SharedSuggestionProvider.class)
public interface SharedSuggestionProviderMixin {

    @Inject(
            method = "lambda$suggestResource$1",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void command_crafter$preventSuggestingGeneratedResources(SuggestionsBuilder suggestionsBuilder, String string, Identifier id, CallbackInfo ci) {
        if(id.getPath().endsWith("--craftergen")) {
            ci.cancel();
        }
    }

    @Inject(
            method = "lambda$suggestResource$3",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void command_crafter$preventSuggestingGeneratedResources(SuggestionsBuilder suggestionsBuilder, Identifier id, CallbackInfo ci) {
        if(id.getPath().endsWith("--craftergen")) {
            ci.cancel();
        }
    }

    @Inject(
            method = "lambda$suggestResource$1",
            at = @At("HEAD"),
            remap = false
    )
    private static void command_crafter$suggestShortId(SuggestionsBuilder suggestionsBuilder, String prefix, Identifier id, CallbackInfo ci) {
        if(!id.getNamespace().equals("minecraft"))
            return;
        final var fullInput = getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT());
        if(fullInput == null)
            return; // Don't modify vanilla suggestions
        suggestionsBuilder.suggest(prefix + id.getPath());
    }

    @Inject(
            method = "lambda$suggestResource$3",
            at = @At("HEAD"),
            remap = false
    )
    private static void command_crafter$suggestShortId(SuggestionsBuilder builder, Identifier id, CallbackInfo ci) {
        if(!id.getNamespace().equals("minecraft"))
            return;
        final var fullInput = getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT());
        if(fullInput == null)
            return; // Don't modify vanilla suggestions
        builder.suggest(id.getPath());
    }

    @Inject(
            method = "lambda$suggestResource$4",
            at = @At("HEAD"),
            remap = false
    )
    private static void command_crafter$suggestShortId(SuggestionsBuilder builder, Function<Object, Identifier> idFun, Function<Object, Message> tooltip, Object v, CallbackInfo ci) {
        final var id = idFun.apply(v);
        if(!id.getNamespace().equals("minecraft"))
            return;
        final var fullInput = getOrNull(VanillaLanguage.Companion.getSUGGESTIONS_FULL_INPUT());
        if(fullInput == null)
            return; // Don't modify vanilla suggestions
        builder.suggest(id.getPath(), tooltip.apply(v));
    }
}
