package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.papierkorb2292.command_crafter.editor.debugger.helper.HoverCursorContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NbtPathArgument.class)
public class NbtPathArgumentMixin implements HoverCursorContainer {

    private int command_crafter$hoverCursor = -1;

    @ModifyExpressionValue(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/commands/arguments/NbtPathArgument$NbtPath;",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lcom/mojang/brigadier/StringReader;canRead()Z"
            )
    )
    private boolean command_crafter$endHoverEvaluationPathAfterCursor(boolean original, StringReader reader) {
        return original && (this.command_crafter$hoverCursor == -1 || this.command_crafter$hoverCursor >= reader.getCursor());
    }

    @Override
    public void command_crafter$setHoverCursor(int cursor) {
        this.command_crafter$hoverCursor = cursor;
    }
}
