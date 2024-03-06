package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.mojang.brigadier.context.ContextChain;
import net.minecraft.command.SingleCommandAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SingleCommandAction.class)
public interface SingleCommandActionAccessor<T> {

    @Accessor
    ContextChain<T> getContextChain();
}
