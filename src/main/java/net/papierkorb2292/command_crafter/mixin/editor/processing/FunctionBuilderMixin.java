package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.command.AbstractServerCommandSource;
import net.minecraft.server.function.CommandFunction;
import net.minecraft.server.function.FunctionBuilder;
import net.papierkorb2292.command_crafter.editor.processing.helper.DocumentationContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FunctionBuilder.class)
public class FunctionBuilderMixin<T extends AbstractServerCommandSource<T>> implements DocumentationContainer {

    @Nullable
    private String command_crafter$documentation;

    @Nullable
    @Override
    public String command_crafter$getDocumentation() {
        return command_crafter$documentation;
    }

    @Override
    public void command_crafter$setDocumentation(@NotNull String documentation) {
        command_crafter$documentation = documentation;
    }

    @ModifyReturnValue(
            method = "toCommandFunction",
            at = @At("RETURN")
    )
    private CommandFunction<T> command_crafter$addDocumentation(CommandFunction<T> function) {
        if(command_crafter$documentation != null && function instanceof DocumentationContainer docsContainer)
            docsContainer.command_crafter$setDocumentation(this.command_crafter$documentation);
        return function;
    }
}
