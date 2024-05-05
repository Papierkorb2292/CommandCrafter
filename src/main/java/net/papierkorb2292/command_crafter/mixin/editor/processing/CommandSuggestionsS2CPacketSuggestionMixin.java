package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.papierkorb2292.command_crafter.editor.processing.helper.SuggestionReplaceEndContainer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.networking.UtilKt.getNULLABLE_VAR_INT_PACKET_CODEC;

@Mixin(CommandSuggestionsS2CPacket.Suggestion.class)
public class CommandSuggestionsS2CPacketSuggestionMixin implements SuggestionReplaceEndContainer {

    private Integer command_crafter$replaceEnd = null;

    @Override
    public void command_crafter$setReplaceEnd(int end) {
        command_crafter$replaceEnd = end;
    }

    @Nullable
    @Override
    public Integer command_crafter$getReplaceEnd() {
        return command_crafter$replaceEnd;
    }

    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/PacketCodec;tuple(Lnet/minecraft/network/codec/PacketCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/PacketCodec;Ljava/util/function/Function;Ljava/util/function/BiFunction;)Lnet/minecraft/network/codec/PacketCodec;"
            )
    )
    private static PacketCodec<RegistryByteBuf, CommandSuggestionsS2CPacket.Suggestion> command_crafter$addReplaceEndCodec(PacketCodec<RegistryByteBuf, CommandSuggestionsS2CPacket.Suggestion> originalCodec) {
        return PacketCodec.tuple(
                originalCodec,
                suggestion -> suggestion,
                getNULLABLE_VAR_INT_PACKET_CODEC(),
                suggestion -> ((SuggestionReplaceEndContainer)(Object)suggestion).command_crafter$getReplaceEnd(),
                (suggestion, replaceEnd) -> {
                    ((SuggestionReplaceEndContainer)(Object)suggestion).command_crafter$setReplaceEnd(replaceEnd);
                    return suggestion;
                }
        );
    }
}
