package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.SemanticResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticTokensCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.command.argument.NbtPathArgumentType;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NbtPathArgumentType.class)
public abstract class NbtPathArgumentTypeMixin implements SemanticCommandNode {
    @Shadow public abstract NbtPathArgumentType.NbtPath parse(StringReader stringReader) throws CommandSyntaxException;

    private static final ThreadLocal<SemanticTokensBuilder> command_crafter$semanticTokensBuilder = new ThreadLocal<>();
    private static final ThreadLocal<Integer> command_crafter$cursorOffset = new ThreadLocal<>();
    @Override
    public void command_crafter$createSemanticTokens(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<SemanticResourceCreator> reader, @NotNull SemanticTokensBuilder tokens) throws CommandSyntaxException {
        command_crafter$semanticTokensBuilder.set(tokens);
        command_crafter$cursorOffset.set(reader.getReadCharacters() + range.getStart());
        try {
            parse(new StringReader(range.get(context.getInput())));
        } finally {
            command_crafter$semanticTokensBuilder.set(null);
        }
    }

    @ModifyArg(
            method = "parseNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/NbtPathArgumentType;readCompoundChildNode(Lcom/mojang/brigadier/StringReader;Ljava/lang/String;)Lnet/minecraft/command/argument/NbtPathArgumentType$PathNode;"
            )
    )
    private static String command_crafter$highlightTag(StringReader reader, String tag) {
        var tokens = command_crafter$semanticTokensBuilder.get();
        if(tokens == null) return tag;

        tokens.addAbsoluteMultiline(reader.getCursor() - tag.length() + command_crafter$cursorOffset.get(), tag.length(), TokenType.Companion.getPROPERTY(), 0);
        return tag;
    }

    @ModifyReceiver(
            method = { "parseNode", "readCompoundChildNode" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseCompound()Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private static StringNbtReader command_crafter$highlightCompounds(StringNbtReader reader) {
        var tokens = command_crafter$semanticTokensBuilder.get();
        if(tokens == null) return reader;

        ((SemanticTokensCreator)reader).command_crafter$setSemanticTokensBuilder(tokens, command_crafter$cursorOffset.get());
        return reader;
    }

    @Inject(
           method = "parseNode",
           at = @At(
                   value = "INVOKE",
                   target = "Lcom/mojang/brigadier/StringReader;readInt()I"
           )
    )
    private static void command_crafter$saveIndexStartCursor(StringReader reader, boolean root, CallbackInfoReturnable<?> cir, @Share("IndexStartCursor") LocalIntRef startCursor) {
        startCursor.set(reader.getCursor());
    }

    @Inject(
            method = "parseNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readInt()I",
                    shift = At.Shift.AFTER
            )
    )
    private static void command_crafter$highlightIndex(StringReader reader, boolean root, CallbackInfoReturnable<?> cir, @Share("IndexStartCursor") LocalIntRef startCursor) {
        var tokens = command_crafter$semanticTokensBuilder.get();
        if(tokens == null) return;

        tokens.addAbsoluteMultiline(startCursor.get() + command_crafter$cursorOffset.get(), reader.getCursor() - startCursor.get(), TokenType.Companion.getNUMBER(), 0);
    }
}