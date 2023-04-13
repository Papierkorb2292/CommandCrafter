package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.SemanticResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticCommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LiteralCommandNode.class)
public class LiteralCommandNodeMixin implements SemanticCommandNode {

    @Override
    public void command_crafter$createSemanticTokens(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<SemanticResourceCreator> reader, @NotNull SemanticTokensBuilder tokens) {
        tokens.addAbsoluteMultiline(range.getStart() + reader.getReadCharacters(), range.getLength(), reader.getLines(), TokenType.Companion.getKEYWORD(), 0);
    }
}