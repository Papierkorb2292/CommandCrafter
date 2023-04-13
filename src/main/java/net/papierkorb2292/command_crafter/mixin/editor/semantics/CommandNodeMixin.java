package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.tree.CommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.RedirectTargetAware;
import net.papierkorb2292.command_crafter.editor.processing.helper.RedirectTargetChildAware;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(CommandNode.class)
public abstract class CommandNodeMixin<S> implements RedirectTargetAware, RedirectTargetChildAware {

    @Shadow public abstract Collection<CommandNode<S>> getChildren();

    private boolean command_crafter$isRedirectTarget;
    private boolean command_crafter$isRedirectTargetChild;

    @Inject(
            method = "addChild",
            at = @At("HEAD"),
            remap = false
    )
    private void command_crafter$makeChildRedirectTargetAware(CommandNode<S> node, CallbackInfo ci) {
        if(node instanceof RedirectTargetChildAware redirectTargetChildAware) {
            redirectTargetChildAware.command_crafter$setIsRedirectTargetChild(command_crafter$isRedirectTarget);
        }
    }

    @Override
    public void command_crafter$setIsRedirectTarget(boolean isRedirectTarget) {
        command_crafter$isRedirectTarget = isRedirectTarget;
        for(var child : getChildren()) {
            if(child instanceof  RedirectTargetChildAware redirectTargetChildAware) {
                redirectTargetChildAware.command_crafter$setIsRedirectTargetChild(isRedirectTarget);
            }
        }
    }

    @Override
    public boolean command_crafter$isRedirectTarget() {
        return command_crafter$isRedirectTarget;
    }

    @Override
    public void command_crafter$setIsRedirectTargetChild(boolean isRedirectTargetChild) {
        command_crafter$isRedirectTargetChild = isRedirectTargetChild;
    }

    @Override
    public boolean command_crafter$isRedirectTargetChild() {
        return command_crafter$isRedirectTargetChild;
    }
}
