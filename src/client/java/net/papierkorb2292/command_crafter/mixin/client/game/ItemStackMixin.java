package net.papierkorb2292.command_crafter.mixin.client.game;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
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
            method = "getTooltipLines",
            at = @At("HEAD")
    )
    private void command_crafter$saveHoveredItem(Item.TooltipContext context, @Nullable Player player, TooltipFlag type, CallbackInfoReturnable<List<Component>> cir) {
        ClientCommandCrafter.INSTANCE.setCurrentlyHoveredItem((ItemStack)(Object)this);
    }
}
