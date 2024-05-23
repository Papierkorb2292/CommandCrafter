package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.command.argument.NbtPathArgumentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NbtPathArgumentType.NbtPath.class)
public interface NbtPathArgumentTypeNbtPathAccessor {
    @Accessor
    Object2IntMap<NbtPathArgumentType.PathNode> getNodeEndIndices();

    @Accessor
    NbtPathArgumentType.PathNode[] getNodes();
}
