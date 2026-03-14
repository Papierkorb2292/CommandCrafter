package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
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
        return !(this.level() instanceof DummyWorld);
    }
}
