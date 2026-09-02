package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
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

    @WrapWithCondition(
            method = "lambda$load$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"
            ),
            remap = false
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
        try {
            readAdditionalSaveData(input);
        } catch (Throwable _) {
            // Don't build crash report, it's not necessary
        }
    }
}
