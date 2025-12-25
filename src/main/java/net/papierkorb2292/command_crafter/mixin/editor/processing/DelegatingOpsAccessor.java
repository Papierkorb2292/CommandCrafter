package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.DelegatingOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DelegatingOps.class)
public interface DelegatingOpsAccessor {

    @Accessor
    DynamicOps<?> getDelegate();
}
