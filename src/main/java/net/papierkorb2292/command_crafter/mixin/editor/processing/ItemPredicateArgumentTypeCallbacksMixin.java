package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Cancellable;
import net.minecraft.item.ItemStack;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Mixin(targets = "net/minecraft/command/argument/ItemPredicateArgumentType$Context")
public class ItemPredicateArgumentTypeCallbacksMixin {
    @ModifyReceiver(
            method = {
                    "itemMatchPredicate(Lcom/mojang/brigadier/ImmutableStringReader;Lnet/minecraft/util/Identifier;)Ljava/util/function/Predicate;",
                    "tagMatchPredicate(Lcom/mojang/brigadier/ImmutableStringReader;Lnet/minecraft/util/Identifier;)Ljava/util/function/Predicate;"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Optional;orElseThrow(Ljava/util/function/Supplier;)Ljava/lang/Object;"
            )
    )
    private static <T, X> Optional<T> command_crafter$allowMalformedTypeId(Optional<T> instance, Supplier<? extends X> exceptionSupplier, @Cancellable CallbackInfoReturnable<Predicate<ItemStack>> ci) {
        if(instance.isEmpty() && PackratParserAdditionalArgs.INSTANCE.shouldAllowMalformed())
            ci.setReturnValue(stack -> true);
        return instance;
    }
}
