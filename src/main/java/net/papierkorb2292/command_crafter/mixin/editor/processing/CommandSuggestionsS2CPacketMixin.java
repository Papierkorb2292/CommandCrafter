package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.SuggestionReplaceEndContainer;
import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

import static net.papierkorb2292.command_crafter.networking.UtilKt.*;

@Mixin(CommandSuggestionsS2CPacket.class)
public class CommandSuggestionsS2CPacketMixin {

    @Shadow @Final private List<CommandSuggestionsS2CPacket.Suggestion> suggestions;
    @Nullable
    private List<? extends CompletionItem> command_crafter$completionItems;

    @Inject(
            method = "<init>(ILcom/mojang/brigadier/suggestion/Suggestions;)V",
            at = @At("RETURN")
    )
    private void command_crafter$saveCompletionItems(int id, Suggestions suggestions, CallbackInfo ci) {
        command_crafter$completionItems = ((CompletionItemsContainer) suggestions).command_crafter$getCompletionItems();
    }

    @ModifyReturnValue(
            method = "method_56609",
            at = @At("RETURN")
    )
    private static CommandSuggestionsS2CPacket.Suggestion command_crafter$modifySuggestions(CommandSuggestionsS2CPacket.Suggestion original, Suggestion suggestion) {
        var replaceEnd = ((SuggestionReplaceEndContainer) (Object) original).command_crafter$getReplaceEnd();
        if (replaceEnd != null)
            ((SuggestionReplaceEndContainer) suggestion).command_crafter$setReplaceEnd(replaceEnd);
        return original;
    }

    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/PacketCodec;tuple(Lnet/minecraft/network/codec/PacketCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/PacketCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/PacketCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/PacketCodec;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;)Lnet/minecraft/network/codec/PacketCodec;"
            )
    )
    private static PacketCodec<RegistryByteBuf, CommandSuggestionsS2CPacket> command_crafter$addCompletionListCodec(PacketCodec<RegistryByteBuf, CommandSuggestionsS2CPacket> original) {
        //noinspection unchecked
        return PacketCodec.tuple(
                original,
                packet -> packet,
                nullable(list(getCOMPLETION_ITEM_PACKET_CODEC())),
                packet -> (List<CompletionItem>) ((CommandSuggestionsS2CPacketMixin)(Object)packet).command_crafter$completionItems,
                (packet, completionList) -> {
                    ((CommandSuggestionsS2CPacketMixin)(Object)packet).command_crafter$completionItems = completionList;
                    return packet;
                }
        );
    }
}
