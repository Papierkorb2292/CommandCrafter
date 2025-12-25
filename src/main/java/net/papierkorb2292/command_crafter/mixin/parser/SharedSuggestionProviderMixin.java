package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SharedSuggestionProvider.class)
public interface SharedSuggestionProviderMixin {

    @Inject(
            method = "method_9266",
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
            method = "method_9275",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void command_crafter$preventSuggestingGeneratedResources(SuggestionsBuilder suggestionsBuilder, Identifier id, CallbackInfo ci) {
        if(id.getPath().endsWith("--craftergen")) {
            ci.cancel();
        }
    }
}
