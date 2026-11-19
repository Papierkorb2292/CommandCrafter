package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DynamicOpsReadView;
import net.papierkorb2292.command_crafter.helper.DummyWorld;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    private Level level;

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;createInterpolationHandler()Lnet/minecraft/world/entity/InterpolationHandler;"
            )
    )
    private InterpolationHandler command_crafter$allowDummyWorldForInterpolationHandler(Entity instance, Operation<InterpolationHandler> original) {
        // InterpolationHandler's usually try to call isClientSide on the level(), which doesn't work for players
        return level instanceof DummyWorld ? InterpolationHandler.NO_OP : original.call(instance);
    }

    @WrapWithCondition(
            method = "load -> accept(Ljava/lang/String;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"
            )
    )
    private boolean command_crafter$suppressMissingTeamWarnWhenAnalyzing(Logger instance, String s, Object o) {
        return !(this.level instanceof DummyWorld);
    }

    @Shadow
    protected abstract void readAdditionalSaveData(ValueInput input);

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

    @WrapMethod(method = "load")
    private void command_crafter$deduplicateEntityAnalyzing(ValueInput input, Operation<Void> original) {
        if(!(input instanceof DynamicOpsReadView<?> readView) || readView.getDeduplicationMarkers().add("Entity")) {
            original.call(input);
            return;
        }
        readAdditionalSaveData(input);
    }
}
