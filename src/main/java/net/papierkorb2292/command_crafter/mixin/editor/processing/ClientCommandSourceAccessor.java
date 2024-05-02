package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.network.ClientCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(ClientCommandSource.class)
public interface ClientCommandSourceAccessor {
    @Accessor
    void setCompletionId(int id);
    @Accessor
    int getCompletionId();
    @Accessor
    void setPendingCommandCompletion(CompletableFuture<Suggestions> pendingCommandCompletion);
}
