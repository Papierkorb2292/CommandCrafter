package net.papierkorb2292.command_crafter.mixin.parser;

import net.minecraft.core.HolderGetter;
import net.minecraft.resources.RegistryOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegistryOps.class)
public interface RegistryOpsAccessor {
    @Accessor
    HolderGetter.Provider getGetterProvider();
}
