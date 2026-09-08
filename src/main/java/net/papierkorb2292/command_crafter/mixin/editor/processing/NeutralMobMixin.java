package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.NeutralMob;
import net.papierkorb2292.command_crafter.helper.DummyWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(NeutralMob.class)
public interface NeutralMobMixin {

    @WrapOperation(
            method = "readPersistentAngerSaveData",
            constant = @Constant(
                    classValue = ServerLevel.class
            )
    )
    private boolean command_crafter$readAngryAtInDummyWorld(Object world, Operation<Boolean> op) {
        return op.call(world) || world instanceof DummyWorld;
    }
}
