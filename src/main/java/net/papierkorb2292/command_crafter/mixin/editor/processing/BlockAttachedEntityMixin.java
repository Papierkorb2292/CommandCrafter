package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.serialization.DataResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.BlockAttachedEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.world.World;
import net.papierkorb2292.command_crafter.editor.processing.DynamicOpsReadView;
import net.papierkorb2292.command_crafter.editor.processing.PreLaunchDecoderOutputTracker;
import net.papierkorb2292.command_crafter.helper.DummyWorld;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockAttachedEntity.class)
public abstract class BlockAttachedEntityMixin extends Entity {
    public BlockAttachedEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @WrapWithCondition(
            method = "readCustomData",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;)V"
            ),
            allow = 1
    )
    private boolean command_crafter$suppressMissingTeamWarnWhenAnalyzing(Logger instance, String s, Object o, ReadView view) {
        if(this.getEntityWorld() instanceof DummyWorld) {
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
