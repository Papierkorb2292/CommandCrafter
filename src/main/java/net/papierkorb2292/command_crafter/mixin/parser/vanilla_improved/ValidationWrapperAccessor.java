package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.commands.arguments.ValidationWrapper;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.Validatable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ValidationWrapper.class)
public interface ValidationWrapperAccessor<T extends Validatable> {
    @Accessor
    Holder<T> getResult();
}
