package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DynamicOpsReadView;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Mob.class)
public abstract class MobMixin extends Entity {
    public MobMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @WrapMethod(method = "readAdditionalSaveData")
    private void command_crafter$deduplicateEntityAnalyzing(ValueInput input, Operation<Void> original) {
        if(!(input instanceof DynamicOpsReadView<?> readView) || readView.getDeduplicationMarkers().add("Mob")) {
            original.call(input);
        }
    }
}
