package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.serialization.DynamicOps;
import net.minecraft.util.dynamic.ForwardingDynamicOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ForwardingDynamicOps.class)
public interface ForwardingDynamicOpsAccessor {

    @Accessor
    DynamicOps<?> getDelegate();
}
