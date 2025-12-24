package net.papierkorb2292.command_crafter.mixin.client.game;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.Component;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Locale;
import java.util.Objects;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Shadow
    public abstract void setClipboard(String clipboard);

    @Shadow
    protected abstract void sendMessage(Text message);

    @ModifyExpressionValue(
            method = "copyLookAt",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/MinecraftClient;crosshairTarget:Lnet/minecraft/util/hit/HitResult;"
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
        var player = MinecraftClient.getInstance().player;
        if(item == null && player != null) {
            // The player is not hovering over an item in the inventory, so use the held items instead
            item = player.getMainHandStack();
            if(item.isEmpty())
                item = player.getOffHandStack();
        }

        if(item == null || item.isEmpty()) {
            return original;
        }

        final var id = item.getRegistryEntry().getIdAsString();
        final var componentBuilder = new StringBuilder();
        final var addedRemovedPair = item.getComponentChanges().toAddedRemovedPair();

        for(final var added : addedRemovedPair.added()) {
            final var encoded = command_crafter$encodeComponent(added);
            if(encoded == null)
                continue;
            if(!componentBuilder.isEmpty())
                componentBuilder.append(',');
            componentBuilder.append(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE.getId(added.type())).toShortString());
            componentBuilder.append('=');
            componentBuilder.append(encoded);
        }
        for(final var removed : addedRemovedPair.removed()) {
            if(!componentBuilder.isEmpty())
                componentBuilder.append(',');
            componentBuilder.append('!');
            componentBuilder.append(Objects.requireNonNull(Registries.DATA_COMPONENT_TYPE.getId(removed)).toShortString());
        }

        if(!componentBuilder.isEmpty()) {
            componentBuilder.insert(0, '[');
            componentBuilder.append(']');
        }

        final var count = item.getCount() > 1 ? " " + item.getCount() : "";

        String giveCommand = String.format(Locale.ROOT, "/give @s %s%s%s", id, componentBuilder, count);
        setClipboard(giveCommand);
        sendMessage(Text.translatable("command_crafter.debug.inspect.item").formatted(Formatting.GREEN));

        return null;
    }

    @Nullable
    private <T> NbtElement command_crafter$encodeComponent(Component<T> component) {
        final var codec = component.type().getCodec();
        if(codec == null) {
            return null;
        }
        final var encoded = codec.encode(
                component.value(),
                RegistryOps.of(NbtOps.INSTANCE, Objects.requireNonNull(MinecraftClient.getInstance().player).getRegistryManager()),
                NbtOps.INSTANCE.empty()
        );
        return encoded.result().orElse(new NbtCompound());
    }
}
