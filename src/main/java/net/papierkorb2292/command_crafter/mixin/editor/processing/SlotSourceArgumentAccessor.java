package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.arguments.SlotSourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.world.item.slot.SlotSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SlotSourceArgument.class)
public interface SlotSourceArgumentAccessor {

    @Accessor
    ArgumentType<Holder<SlotSource>> getHolderArgument();
}
