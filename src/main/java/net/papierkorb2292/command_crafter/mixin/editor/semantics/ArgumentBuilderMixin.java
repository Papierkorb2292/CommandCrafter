package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.RedirectTargetAware;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArgumentBuilder.class)
public abstract class ArgumentBuilderMixin<S, T> {
    @Inject(
            method = "forward",
            at = @At("HEAD"),
            remap = false
    )
    private void command_crafter$makeTargetChildrenAwareOfRedirect(CommandNode<S> target, RedirectModifier<S> modifier, boolean fork, CallbackInfoReturnable<T> cir) {
        if(target instanceof RedirectTargetAware redirectTargetAware) {
            redirectTargetAware.command_crafter$setIsRedirectTarget(true);
        }
    }
}
