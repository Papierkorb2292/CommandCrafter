package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
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

@Mixin(NbtPathArgumentType.class)
public abstract class NbtPathArgumentTypeMixin implements AnalyzingCommandNode {
    @Shadow public abstract NbtPathArgumentType.NbtPath parse(StringReader stringReader) throws CommandSyntaxException;

    private static final ThreadLocal<AnalyzingResult> command_crafter$analyzingResult = new ThreadLocal<>();
    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
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
                    target = "Lnet/minecraft/command/argument/NbtPathArgumentType;readCompoundChildNode(Lcom/mojang/brigadier/StringReader;Ljava/lang/String;)Lnet/minecraft/command/argument/NbtPathArgumentType$PathNode;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/command/argument/NbtPathArgumentType;readName(Lcom/mojang/brigadier/StringReader;)Ljava/lang/String;"
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

    @SuppressWarnings("unused")
    @WrapOperation(
            method = { "parseNode", "readCompoundChildNode" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readCompoundAsArgument(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private static NbtCompound command_crafter$highlightNbtOption(StringReader reader, Operation<NbtCompound> op) throws CommandSyntaxException {
        var analyzingResult = command_crafter$analyzingResult.get();
        if(analyzingResult == null)
            return op.call(reader);
        //noinspection unchecked
        var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)reader;
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        var nbtReader = StringNbtReader.fromOps(NbtOps.INSTANCE);
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        var nbt = nbtReader.readAsArgument(directiveReader);
        var tree = treeBuilder.build(nbt);
        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        ).analyzeFull(analyzingResult, true, null);
        return nbt instanceof NbtCompound ? (NbtCompound)nbt : null;
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