package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.papierkorb2292.command_crafter.helper.DummyWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {

    @Shadow
    private Level level;

    @WrapMethod(method = "registryAccess")
    private RegistryAccess command_crafter$allowDummyWorldForPlayer(Operation<RegistryAccess> original) {
        return level instanceof DummyWorld ? level.registryAccess() : original.call();
    }

    @WrapWithCondition(
            method = "load",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setGlowingTag(Z)V"
            )
    )
    private boolean command_crafter$skipSetGlowingInDummyWorld(Entity instance, boolean value) {
        return !(level instanceof DummyWorld);
    }
}
