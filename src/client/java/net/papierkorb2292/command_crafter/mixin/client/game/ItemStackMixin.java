package net.papierkorb2292.command_crafter.mixin.client.game;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    @Inject(
            method = "getTooltip",
            at = @At("HEAD")
    )
    private void command_crafter$saveHoveredItem(Item.TooltipContext context, @Nullable PlayerEntity player, TooltipType type, CallbackInfoReturnable<List<Text>> cir) {
        ClientCommandCrafter.INSTANCE.setCurrentlyHoveredItem((ItemStack)(Object)this);
    }
}
