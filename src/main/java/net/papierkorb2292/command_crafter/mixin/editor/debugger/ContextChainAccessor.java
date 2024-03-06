package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ContextChain.class)
public interface ContextChainAccessor<S> {

    @Accessor(remap = false)
    CommandContext<S> getExecutable();

    @Accessor(remap = false)
    List<CommandContext<S>> getModifiers();
}
