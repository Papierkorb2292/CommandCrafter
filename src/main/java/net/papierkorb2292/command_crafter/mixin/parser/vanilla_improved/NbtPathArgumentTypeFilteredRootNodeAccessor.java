package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.nbt.CompoundTag;
import net.papierkorb2292.command_crafter.parser.helper.NbtPathFilteredRootNodeFilterProvider;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.commands.arguments.NbtPathArgument$MatchRootObjectNode")
public class NbtPathArgumentTypeFilteredRootNodeAccessor implements NbtPathFilteredRootNodeFilterProvider {
    private CompoundTag command_crafter$filter;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void command_crafter$storeFilterCompound(CompoundTag filter, CallbackInfo ci) {
        this.command_crafter$filter = filter;
    }

    @NotNull
    @Override
    public CompoundTag command_crafter$getFilter() {
        return this.command_crafter$filter;
    }
}
