package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NbtPathArgumentType.class)
public abstract class NbtPathArgumentTypeMixin implements AnalyzingCommandNode {
    @Shadow public abstract NbtPathArgumentType.NbtPath parse(StringReader stringReader) throws CommandSyntaxException;

    private static final ThreadLocal<AnalyzingResult> command_crafter$analyzingResult = new ThreadLocal<>();
    @Override
    public void command_crafter$analyze(@NotNull CommandContext<ServerCommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        command_crafter$analyzingResult.set(result);
        try {
            parse(new StringReader(range.get(context.getInput())));
        } finally {
            command_crafter$analyzingResult.remove();
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
        var analyzingResult = command_crafter$analyzingResult.get();
        if(analyzingResult == null) return tag;

        analyzingResult.getSemanticTokens().addAbsoluteMultiline(reader.getCursor() - tag.length(), tag.length(), TokenType.Companion.getPROPERTY(), 0);
        return tag;
    }

    @SuppressWarnings("unused")
    @ModifyReceiver(
            method = { "parseNode", "readCompoundChildNode" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseCompound()Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private static StringNbtReader command_crafter$highlightCompounds(StringNbtReader reader) {
        var analyzingResult = command_crafter$analyzingResult.get();
        if(analyzingResult == null) return reader;

        ((AnalyzingResultCreator)reader).command_crafter$setAnalyzingResult(analyzingResult);
        return reader;
    }

    @Inject(
           method = "parseNode",
           at = @At(
                   value = "INVOKE",
                   target = "Lcom/mojang/brigadier/StringReader;readInt()I",
                   remap = false
           )
    )
    private static void command_crafter$saveIndexStartCursor(StringReader reader, boolean root, CallbackInfoReturnable<?> cir, @Share("IndexStartCursor") LocalIntRef startCursorRef) {
        startCursorRef.set(reader.getCursor());
    }

    @Inject(
            method = "parseNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readInt()I",
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private static void command_crafter$highlightIndex(StringReader reader, boolean root, CallbackInfoReturnable<?> cir, @Share("IndexStartCursor") LocalIntRef startCursorRef) {
        var analyzingResult = command_crafter$analyzingResult.get();
        if(analyzingResult == null) return;

        analyzingResult.getSemanticTokens().addAbsoluteMultiline(startCursorRef.get(), reader.getCursor() - startCursorRef.get(), TokenType.Companion.getNUMBER(), 0);
    }
}