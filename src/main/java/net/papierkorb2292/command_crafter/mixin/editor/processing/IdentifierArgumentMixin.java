package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.commands.arguments.IdentifierArgument;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(IdentifierArgument.class)
public class IdentifierArgumentMixin implements PackContentFileTypeContainer {

    private PackContentFileType command_crafter$packContentFileType = null;

    @Override
    public void command_crafter$setPackContentFileType(@NotNull PackContentFileType packContentFileType) {
        command_crafter$packContentFileType = packContentFileType;
    }

    @Nullable
    @Override
    public PackContentFileType command_crafter$getPackContentFileType() {
        return command_crafter$packContentFileType;
    }
}
