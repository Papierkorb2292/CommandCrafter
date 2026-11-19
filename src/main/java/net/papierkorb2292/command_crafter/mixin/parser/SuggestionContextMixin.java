package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.suggestions.SuggestionContext;
import net.minecraft.resources.Identifier;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(SuggestionContext.class)
public interface SuggestionContextMixin {

    @Inject(
            method = "suggestResource(Ljava/lang/Iterable;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture; ->{1} accept(Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void command_crafter$preventSuggestingGeneratedResources(SuggestionsBuilder suggestionsBuilder, String string, Identifier id, CallbackInfo ci) {
        if(id.getPath().endsWith("--craftergen")) {
            ci.cancel();
        }
    }

    @Inject(
            method = "suggestResource(Ljava/lang/Iterable;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture; ->{1} accept(Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void command_crafter$preventSuggestingGeneratedResources(SuggestionsBuilder suggestionsBuilder, Identifier id, CallbackInfo ci) {
        if(id.getPath().endsWith("--craftergen")) {
            ci.cancel();
        }
    }

    @Inject(
            method = "suggestResource(Ljava/lang/Iterable;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture; ->{1} accept(Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD")
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
            method = "suggestResource(Ljava/lang/Iterable;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture; ->{1} accept(Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD")
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
            method = "suggestResource(Ljava/lang/Iterable;Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture; ->{1} accept",
            at = @At("HEAD")
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
