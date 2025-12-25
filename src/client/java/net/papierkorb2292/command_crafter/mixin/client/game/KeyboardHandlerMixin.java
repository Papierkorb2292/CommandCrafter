package net.papierkorb2292.command_crafter.mixin.client.game;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.HitResult;
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Locale;
import java.util.Objects;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Shadow
    public abstract void setClipboard(String clipboard);

    @Shadow
    protected abstract void showDebugChat(Component message);

    @ModifyExpressionValue(
            method = "copyRecreateCommand",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;hitResult:Lnet/minecraft/world/phys/HitResult;"
            )
    )
    private HitResult command_crafter$copyItemsWithDebugCopy(HitResult original) {
        // Only copy items when the player is not looking at something or hovering over an item in the inventory
        if(original == null) {
            return null;
        }
        ItemStack item = ClientCommandCrafter.INSTANCE.getCurrentlyHoveredItem();
        // Only copy items if the player is not looking at anything or is already hovering over an item
        if (original.getType() != HitResult.Type.MISS && item == null) {
            return original;
        }
        var player = Minecraft.getInstance().player;
        if(item == null && player != null) {
            // The player is not hovering over an item in the inventory, so use the held items instead
            item = player.getMainHandItem();
            if(item.isEmpty())
                item = player.getOffhandItem();
        }

        if(item == null || item.isEmpty()) {
            return original;
        }

        final var id = item.getItemHolder().getRegisteredName();
        final var componentBuilder = new StringBuilder();
        final var addedRemovedPair = item.getComponentsPatch().split();

        for(final var added : addedRemovedPair.added()) {
            final var encoded = command_crafter$encodeComponent(added);
            if(encoded == null)
                continue;
            if(!componentBuilder.isEmpty())
                componentBuilder.append(',');
            componentBuilder.append(Objects.requireNonNull(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(added.type())).toShortString());
            componentBuilder.append('=');
            componentBuilder.append(encoded);
        }
        for(final var removed : addedRemovedPair.removed()) {
            if(!componentBuilder.isEmpty())
                componentBuilder.append(',');
            componentBuilder.append('!');
            componentBuilder.append(Objects.requireNonNull(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(removed)).toShortString());
        }

        if(!componentBuilder.isEmpty()) {
            componentBuilder.insert(0, '[');
            componentBuilder.append(']');
        }

        final var count = item.getCount() > 1 ? " " + item.getCount() : "";

        String giveCommand = String.format(Locale.ROOT, "/give @s %s%s%s", id, componentBuilder, count);
        setClipboard(giveCommand);
        showDebugChat(Component.translatable("command_crafter.debug.inspect.item").withStyle(ChatFormatting.GREEN));

        return null;
    }

    @Nullable
    private <T> Tag command_crafter$encodeComponent(TypedDataComponent<T> component) {
        final var codec = component.type().codec();
        if(codec == null) {
            return null;
        }
        final var encoded = codec.encode(
                component.value(),
                RegistryOps.create(NbtOps.INSTANCE, Objects.requireNonNull(Minecraft.getInstance().player).registryAccess()),
                NbtOps.INSTANCE.empty()
        );
        return encoded.result().orElse(new CompoundTag());
    }
}
