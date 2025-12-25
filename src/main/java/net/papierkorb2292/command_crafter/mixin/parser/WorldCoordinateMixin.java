package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.commands.arguments.coordinates.WorldCoordinate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@SuppressWarnings("unused")
@Mixin(WorldCoordinate.class)
public class WorldCoordinateMixin {

    @ModifyExpressionValue(
            method = {
                    "parseDouble(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/coordinates/WorldCoordinate;",
                    "parseInt(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/commands/arguments/coordinates/WorldCoordinate;"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    ordinal = 1,
                    remap = false
            )
    )
    private static char command_crafter$allowTildeAtEndOfLine(char c) {
        return c == '\n' ? ' ' : c;
    }
}
