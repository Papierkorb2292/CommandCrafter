package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor<T extends BlockEntity> {
    @Accessor
    BlockEntityType.BlockEntitySupplier<T> getFactory();
    @Accessor
    Set<Block> getValidBlocks();;
}
