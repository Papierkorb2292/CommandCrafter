package net.papierkorb2292.command_crafter.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {

    @ModifyReceiver(
            method = "updateCommandInfo",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Suggestions> command_crafter$ensureCompletionFutureRunsOnRenderThread(CompletableFuture<Suggestions> instance, Consumer<?> consumer) {
        // This is necessary, because UnicodeNameSuggestionSupplier could have switched the future to a different thread,
        // which would throw an error in showSuggestions at this.font.width
        return instance.thenApplyAsync(Function.identity(), Minecraft.getInstance());
    }
}
