package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.ContextChain;
import net.minecraft.commands.execution.tasks.BuildContexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BuildContexts.class)
public interface BuildContextsAccessor<T> {

    @Accessor
    ContextChain<T> getCommand();
}
