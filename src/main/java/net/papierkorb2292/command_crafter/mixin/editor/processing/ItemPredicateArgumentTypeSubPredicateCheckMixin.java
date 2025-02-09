package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtElement;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(targets = "net.minecraft.command.argument.ItemPredicateArgumentType$SubPredicateCheck")
public class ItemPredicateArgumentTypeSubPredicateCheckMixin {
    @ModifyReceiver(
            method = "createPredicate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Decoder;parse(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
                    remap = false
            )
    )
    private Decoder<?> command_crafter$invokeDelayedDecodeNbtAnalyzing(Decoder<?> instance, DynamicOps<?> ops, Object input) {
        final var callback = getOrNull(PackratParserAdditionalArgs.INSTANCE.getDelayedDecodeNbtAnalyzeCallback());
        if (callback != null) {
            //noinspection unchecked
            callback.invoke(((DynamicOps<NbtElement>)ops), instance);
        }
        return instance;
    }
}
