package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.SuggestionReplaceEndContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.papierkorb2292.command_crafter.editor.processing.helper.UtilKt.readNullableCompletionItems;
import static net.papierkorb2292.command_crafter.editor.processing.helper.UtilKt.writeNullableCompletionItems;
import static net.papierkorb2292.command_crafter.networking.UtilKt.readNullableVarInt;
import static net.papierkorb2292.command_crafter.networking.UtilKt.writeNullableVarInt;

@Mixin(CommandSuggestionsS2CPacket.class)
public class CommandSuggestionsS2CPacketMixin {

    @Shadow @Final private Suggestions suggestions;

    @Inject(
            method = "write",
            at = @At("TAIL")
    )
    private void command_crafter$writeAdditionalCompletionItems(PacketByteBuf buf, CallbackInfo ci) {
        writeNullableCompletionItems(buf, ((CompletionItemsContainer)this.suggestions).command_crafter$getCompletionItems());
    }

    @Inject(
            method = "<init>(Lnet/minecraft/network/PacketByteBuf;)V",
            at = @At("TAIL")
    )
    private void command_crafter$readAdditionalCompletionItems(PacketByteBuf buf, CallbackInfo ci) {
        var additionalCompletionItems = readNullableCompletionItems(buf);
        if(additionalCompletionItems != null)
            ((CompletionItemsContainer)this.suggestions).command_crafter$setCompletionItem(additionalCompletionItems);
    }

    @Inject(
            method = "method_34118",
            at = @At("TAIL")
    )
    private static void command_crafter$writeSuggestionEnd(PacketByteBuf buf, Suggestion suggestion, CallbackInfo ci) {
        writeNullableVarInt(buf, ((SuggestionReplaceEndContainer)suggestion).command_crafter$getReplaceEnd());
    }

    @ModifyReturnValue(
            method = "method_34117",
            at = @At("TAIL")
    )
    private static Suggestion command_crafter$readSuggestionEnd(Suggestion suggestion, StringRange range, PacketByteBuf buf) {
        var suggestionEnd = readNullableVarInt(buf);
        if(suggestionEnd != null)
            ((SuggestionReplaceEndContainer)suggestion).command_crafter$setReplaceEnd(suggestionEnd);
        return suggestion;
    }
}
