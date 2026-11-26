package net.papierkorb2292.command_crafter.mixin.test;

import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerFunctionManager.class)
public interface ServerFunctionManagerAccessor {
    @Accessor
    ServerFunctionLibrary getLibrary();
}
