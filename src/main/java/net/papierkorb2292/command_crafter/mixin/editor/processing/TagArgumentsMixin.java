package net.papierkorb2292.command_crafter.mixin.editor.processing;

import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding;
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({CompoundTagArgument.class, NbtPathArgument.class})
public class TagArgumentsMixin implements DataObjectSourceContainer {

    private DataObjectDecoding.DataObjectSource command_crafter$dataObjectSource;

    @Override
    public void command_crafter$setDataObjectSource(@NotNull DataObjectDecoding.DataObjectSource dataObjectSource) {
        command_crafter$dataObjectSource = dataObjectSource;
    }

    @Override
    public @Nullable DataObjectDecoding.DataObjectSource command_crafter$getDataObjectSource() {
        return command_crafter$dataObjectSource;
    }
}
