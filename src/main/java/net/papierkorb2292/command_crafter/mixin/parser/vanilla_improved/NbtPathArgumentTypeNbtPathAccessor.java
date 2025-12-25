package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.commands.arguments.NbtPathArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NbtPathArgument.NbtPath.class)
public interface NbtPathArgumentTypeNbtPathAccessor {
    @Accessor
    Object2IntMap<NbtPathArgument.Node> getNodeToOriginalPosition();

    @Accessor
    NbtPathArgument.Node[] getNodes();
}
