package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.RegistryKeyArgumentType;
import net.minecraft.registry.RegistryKey;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(RegistryKeyArgumentType.class)
public class RegistryKeyArgumentTypeMixin implements AnalyzingCommandNode, PackContentFileTypeContainer {

    private PackContentFileType command_crafter$packContentFileType = null;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        if (command_crafter$packContentFileType != null) {
            IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(context.getArgument(name, RegistryKey.class).getValue(), command_crafter$packContentFileType, range, result, reader);
            return;
        }
        result.getSemanticTokens().addMultiline(range, TokenType.Companion.getPARAMETER(), 0);
    }

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
