package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NbtPathArgument.class)
public abstract class NbtPathArgumentMixin implements AnalyzingCommandNode {
    @Shadow public abstract NbtPathArgument.NbtPath parse(StringReader stringReader) throws CommandSyntaxException;

    private static final ThreadLocal<AnalyzingResult> command_crafter$analyzingResult = new ThreadLocal<>();
    @Override
    public void command_crafter$analyze(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        command_crafter$analyzingResult.set(result);
        try {
            parse(reader);
        } finally {
            command_crafter$analyzingResult.remove();
        }
    }

    @ModifyArg(
            method = "parseNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/NbtPathArgument;readObjectNode(Lcom/mojang/brigadier/StringReader;Ljava/lang/String;)Lnet/minecraft/commands/arguments/NbtPathArgument$Node;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/commands/arguments/NbtPathArgument;readUnquotedName(Lcom/mojang/brigadier/StringReader;)Ljava/lang/String;"
                    )
            )
    )
    private static String command_crafter$highlightUnquotedTag(StringReader reader, String tag) {
        var analyzingResult = command_crafter$analyzingResult.get();
        if(analyzingResult == null) return tag;

        analyzingResult.getSemanticTokens().addMultiline(reader.getCursor() - tag.length(), tag.length(), TokenType.Companion.getPROPERTY(), 0);
        return tag;
    }

    @WrapOperation(
            method = "parseNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readString()Ljava/lang/String;",
                    remap = false
            ),
            allow = 1
    )
    private static String command_crafter$highlightQuotedTag(StringReader reader, Operation<String> op) {
        var analyzingResult = command_crafter$analyzingResult.get();
        if(analyzingResult == null) return op.call(reader);
        final var startCursor = reader.getCursor();
        final var tag = op.call(reader);
        analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPROPERTY(), 0);
        return tag;
    }

    @ModifyExpressionValue(
            method = "readObjectNode",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;isEmpty()Z"
            )
    )
    private static boolean command_crafter$allowEmptyTagWhenAnalyzing(boolean isEmpty) {
        return isEmpty && command_crafter$analyzingResult.get() == null;
    }

    @ModifyExpressionValue(
            method = "readUnquotedName",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;getCursor()I",
                    ordinal = 1,
                    remap = false
            )
    )
    private static int command_crafter$allowEmptyUnquotedTagWhenAnalyzing(int endCursor) {
        return command_crafter$analyzingResult.get() == null ? endCursor : -1;
    }

    @SuppressWarnings("unused")
    @WrapOperation(
            method = {"parseNode", "readObjectNode"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/TagParser;parseCompoundAsArgument(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/nbt/CompoundTag;"
            )
    )
    private static CompoundTag command_crafter$highlightNbtOption(StringReader reader, Operation<CompoundTag> op) throws CommandSyntaxException {
        var analyzingResult = command_crafter$analyzingResult.get();
        if(analyzingResult == null)
            return op.call(reader);
        //noinspection unchecked
        var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)reader;
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        var nbtReader = TagParser.create(NbtOps.INSTANCE);
        //noinspection unchecked
        ((StringRangeTreeCreator<Tag>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        var nbt = nbtReader.parseAsArgument(directiveReader);
        var tree = treeBuilder.build(nbt);
        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        ).analyzeFull(analyzingResult, true, null);
        return nbt instanceof CompoundTag ? (CompoundTag)nbt : null;
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

        analyzingResult.getSemanticTokens().addMultiline(startCursorRef.get(), reader.getCursor() - startCursorRef.get(), TokenType.Companion.getNUMBER(), 0);
    }
}