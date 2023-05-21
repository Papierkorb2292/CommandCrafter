package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import net.minecraft.command.argument.NumberRangeArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NumberRangeArgumentType.class)
public interface NumberRangeArgumentTypeMixin extends AnalyzingCommandNode {

    @Override
    default void command_crafter$analyze(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) {
        result.getSemanticTokens().addAbsoluteMultiline(range.getStart() + reader.getReadCharacters(), range.getLength(), TokenType.Companion.getNUMBER(), 0);
    }
}
