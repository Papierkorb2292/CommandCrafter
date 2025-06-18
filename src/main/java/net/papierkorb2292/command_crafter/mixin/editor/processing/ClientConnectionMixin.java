package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.network.ClientConnection;
import net.papierkorb2292.command_crafter.editor.processing.ArgumentTypeAdditionalDataSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
    @ModifyArg(
            method = "sendImmediately",
            at = @At(
                    value = "INVOKE",
                    target = "Lio/netty/channel/EventLoop;execute(Ljava/lang/Runnable;)V",
                    remap = false
            )
    )
    private Runnable command_crafter$keepThreadLocalsWhenDelayingSend(Runnable original) {
        var shouldWriteAdditionalDataTypes = getOrNull(ArgumentTypeAdditionalDataSerializer.Companion.getShouldWriteAdditionalDataTypes());
        return () -> {
            if(shouldWriteAdditionalDataTypes != null)
                ArgumentTypeAdditionalDataSerializer.Companion.getShouldWriteAdditionalDataTypes().set(shouldWriteAdditionalDataTypes);
            try {
                original.run();
            } finally {
                if(shouldWriteAdditionalDataTypes != null)
                    ArgumentTypeAdditionalDataSerializer.Companion.getShouldWriteAdditionalDataTypes().remove();
            }
        };
    }
}
