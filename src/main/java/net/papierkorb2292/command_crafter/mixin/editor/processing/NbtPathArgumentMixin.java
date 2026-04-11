package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.*;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.command_arguments.NbtPathArgumentAnalyzer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTree;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(NbtPathArgument.class)
public abstract class NbtPathArgumentMixin {

    @ModifyReturnValue(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/commands/arguments/NbtPathArgument$NbtPath;",
            at = @At("RETURN")
    )
    private NbtPathArgument.NbtPath command_crafter$analyzeTrailingDot(NbtPathArgument.NbtPath original, StringReader reader) {
        if(!original.toString().isEmpty() && (!reader.canRead(0) || reader.peek(-1) != '.'))
            return original; // Only add another key access if the path is empty or has trailing dot
        final var pathBuilder = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentPathBuilder());
        if(pathBuilder == null)
            return original;
        pathBuilder.addKeyAccess("", StringRange.at(reader.getCursor()), true);
        return original;
    }

    @Definition(id = "readObjectNode", method = "Lnet/minecraft/commands/arguments/NbtPathArgument;readObjectNode(Lcom/mojang/brigadier/StringReader;Ljava/lang/String;)Lnet/minecraft/commands/arguments/NbtPathArgument$Node;")
    @Expression("readObjectNode(?, @(?))")
    @WrapOperation(
            method = "parseNode",
            at = @At("MIXINEXTRAS:EXPRESSION"),
            allow = 2,
            require = 2
    )
    private static String command_crafter$analyzeTag(StringReader reader, Operation<String> op) {
        final var startCursor = reader.getCursor();
        final var tag = op.call(reader);

        final var analyzingResult = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentAnalyzingResult());
        if(analyzingResult != null)
            analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPROPERTY(), 0);

        final var pathBuilder = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentPathBuilder());
        if(pathBuilder != null)
            pathBuilder.addKeyAccess(tag, new StringRange(startCursor, reader.getCursor()), false);

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
        return isEmpty && getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentAnalyzingResult()) == null;
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
        var analyzingResult = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentAnalyzingResult());
        return analyzingResult == null ? endCursor : -1;
    }

    @WrapOperation(
            method = "parseNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/TagParser;parseCompoundAsArgument(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/nbt/CompoundTag;",
                    ordinal = 1
            )
    )
    private static CompoundTag command_crafter$analyzeListFilter(StringReader reader, Operation<CompoundTag> op) throws CommandSyntaxException {
        final var analyzingResult = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentAnalyzingResult());
        final var pathBuilder = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentPathBuilder());
        if(analyzingResult == null && pathBuilder == null)
            return op.call(reader);
        //noinspection unchecked
        var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)reader;
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        final var list = new ListTag();
        final var startCursor = reader.getCursor() - 1; // Include '['
        treeBuilder.addNodeOrder(list);
        var nbtReader = TagParser.create(NbtOps.INSTANCE);
        if(pathBuilder != null) {
            //noinspection unchecked
            ((StringRangeTreeCreator<Tag>) nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        }
        if(analyzingResult != null) {
            ((AllowMalformedContainer) nbtReader).command_crafter$setAllowMalformed(true);
            ((AnalyzingResultCreator) nbtReader).command_crafter$setAnalyzingResult(analyzingResult);
        }
        var nbt = nbtReader.parseAsArgument(directiveReader);
        if(pathBuilder != null) {
            list.add(nbt);
            treeBuilder.addNode(list, new StringRange(startCursor, reader.getCursor()), startCursor);
            var tree = treeBuilder.build(list);
            pathBuilder.addFilter(tree);
        }
        return nbt instanceof CompoundTag ? (CompoundTag)nbt : null;
    }

    @WrapOperation(
            method = { "parseNode", "readObjectNode" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/TagParser;parseCompoundAsArgument(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/nbt/CompoundTag;",
                    ordinal = 0
            )
    )
    private static CompoundTag command_crafter$analyzeCompoundFilter(StringReader reader, Operation<CompoundTag> op) throws CommandSyntaxException {
        final var analyzingResult = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentAnalyzingResult());
        final var pathBuilder = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentPathBuilder());
        if(analyzingResult == null && pathBuilder == null)
            return op.call(reader);
        //noinspection unchecked
        var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)reader;
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        var nbtReader = TagParser.create(NbtOps.INSTANCE);
        if(pathBuilder != null) {
            //noinspection unchecked
            ((StringRangeTreeCreator<Tag>) nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        }
        if(analyzingResult != null) {
            ((AllowMalformedContainer) nbtReader).command_crafter$setAllowMalformed(true);
            ((AnalyzingResultCreator) nbtReader).command_crafter$setAnalyzingResult(analyzingResult);
        }
        var nbt = nbtReader.parseAsArgument(directiveReader);
        if(pathBuilder != null) {
            var tree = treeBuilder.build(nbt);
            pathBuilder.addFilter(tree);
        }
        return nbt instanceof CompoundTag ? (CompoundTag)nbt : null;
    }

    @WrapOperation(
            method = "parseNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C"
            )
    )
    private static char command_crafter$allowMissingIndex(StringReader instance, Operation<Character> original) {
        final var analyzingResult = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentAnalyzingResult());
        if(analyzingResult != null && !instance.canRead())
            return ' ';
        return original.call(instance);
    }

    @WrapOperation(
            method = "parseNode",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readInt()I"
            )
    )
    private static int command_crafter$analyzeIntIndex(StringReader reader, Operation<Integer> op) throws CommandSyntaxException {
        final var analyzingResult = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentAnalyzingResult());
        final var pathBuilder = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentPathBuilder());
        if(analyzingResult == null && pathBuilder == null)
            return op.call(reader);

        final var indexStart = reader.getCursor();
        final var listStart = indexStart - 1;
        int index;
        try {
            index = MixinUtil.<Integer, CommandSyntaxException>callWithThrows(op, reader);
        } catch(CommandSyntaxException e) {
            if(analyzingResult == null) {
                // Don't be lenient
                throw e;
            }
            index = 0;
        }
        final var indexEnd = reader.getCursor();
        if(analyzingResult != null) {
            analyzingResult.getSemanticTokens().addMultiline(indexStart, indexEnd - indexStart, TokenType.Companion.getNUMBER(), 0);
        }
        if(pathBuilder != null) {
            var listEnd = indexEnd;
            if(reader.canRead() && reader.peek() == ']')
                listEnd++;
            pathBuilder.addListAccess(new StringRange(listStart, listEnd), indexEnd);
        }

        return index;
    }

    @Inject(
            method = "parseNode",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/commands/arguments/NbtPathArgument$AllElementsNode;INSTANCE:Lnet/minecraft/commands/arguments/NbtPathArgument$AllElementsNode;",
                    opcode = Opcodes.GETSTATIC
            )
    )
    private static void command_crafter$analyzeEmptyIndex(StringReader reader, boolean firstNode, CallbackInfoReturnable<NbtPathArgument.Node> cir) {
        final var pathBuilder = getOrNull(NbtPathArgumentAnalyzer.Companion.getCurrentPathBuilder());
        if(pathBuilder != null) {
            final var listEnd = reader.getCursor();
            pathBuilder.addListAccess(new StringRange(listEnd - 2, listEnd), listEnd - 1);
        }
    }
}