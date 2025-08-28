package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.RedirectTargetChildAware;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Predicate;

@Mixin(LiteralCommandNode.class)
public abstract class LiteralCommandNodeMixin<S> extends CommandNode<S> implements AnalyzingCommandNode, RedirectTargetChildAware {

    protected LiteralCommandNodeMixin(Command<S> command, Predicate<S> requirement, CommandNode<S> redirect, RedirectModifier<S> modifier, boolean forks) {
        super(command, requirement, redirect, modifier, forks);
    }

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) {
        // Highlight up until the next space instead of just highlighting the given range,
        // because otherwise it can highlight the entire rest of the line when invoked through tryAnalyzeNextNode,
        // which is especially problematic for macros, where there might be more nodes later in the line
        final var startCursor = reader.getCursor();
        while(reader.canRead() && reader.peek() != ' ')
            reader.skip();
        result.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, command_crafter$isRedirectTargetChild() ? TokenType.Companion.getMACRO() : TokenType.Companion.getKEYWORD(), 0);
    }
}
