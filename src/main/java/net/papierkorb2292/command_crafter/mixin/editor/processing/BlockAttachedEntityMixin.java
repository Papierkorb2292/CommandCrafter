package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.serialization.DataResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.Level;
import net.papierkorb2292.command_crafter.editor.processing.DynamicOpsReadView;
import net.papierkorb2292.command_crafter.editor.processing.PreLaunchDecoderOutputTracker;
import net.papierkorb2292.command_crafter.helper.DummyWorld;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockAttachedEntity.class)
public abstract class BlockAttachedEntityMixin extends Entity {
    public BlockAttachedEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapWithCondition(
            method = "readAdditionalSaveData",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V",
                    remap = false
            ),
            allow = 1
    )
    private boolean command_crafter$suppressMissingTeamWarnWhenAnalyzing(Logger instance, String s, Object o, ValueInput view) {
        if(this.level() instanceof DummyWorld) {
            if(!(view instanceof DynamicOpsReadView<?> dynamicOpsReadView)) return false;
            // Error is only added when block pos is null. The other possible case is that the block pos is too far away from the entities position,
            // but this can't be checked when analyzing since relative coordinates might be used in the command.
            if(o != null) return false;
            PreLaunchDecoderOutputTracker.INSTANCE.onDecoded(
                    DataResult.error(() -> s.replace("{}", "null")),
                    dynamicOpsReadView.getDynamic().getValue()
            );
            return false;
        }
        return true;
    }
}
