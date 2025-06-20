package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Decoder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.*;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockArgumentParser.class)
public class BlockArgumentParserMixin implements AnalyzingResultCreator {

    @Shadow @Final private StringReader reader;
    @Shadow private @Nullable RegistryEntryList<Block> tagId;
    @Shadow @Final private boolean allowTag;
    @Shadow private @Nullable BlockState blockState;
    private AnalyzingResult command_crafter$analyzingResult;

    @Override
    public void command_crafter$setAnalyzingResult(@Nullable AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Inject(
            method = "parseBlockId",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Identifier;fromCommandInput(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/util/Identifier;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$highlightBlockId(CallbackInfo ci, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @Inject(
            method = "parseBlockProperties",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readString()Ljava/lang/String;",
                    ordinal = 0,
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void command_crafter$highlightBlockPropertyName(CallbackInfo ci, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @Inject(
            method = "parseBlockProperties",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readString()Ljava/lang/String;",
                    ordinal = 1,
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void command_crafter$highlightBlockPropertyValue(CallbackInfo ci, @Local(ordinal = 1) int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @ModifyExpressionValue(
            method = "parseTagId",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Identifier;fromCommandInput(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/util/Identifier;"
            )
    )
    private Identifier command_crafter$analyzeTagId(Identifier id, @Local int startCursor) {
        if(command_crafter$analyzingResult != null && reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            //noinspection unchecked
            IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(
                    id,
                    PackContentFileType.BLOCK_TAGS_FILE_TYPE,
                    new StringRange(startCursor, reader.getCursor()),
                    command_crafter$analyzingResult,
                    (DirectiveStringReader<AnalyzingResourceCreator>) directiveStringReader
            );
        }
        return id;
    }

    @ModifyExpressionValue(
            method = "parse",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Lcom/mojang/brigadier/StringReader;peek()C",
                    remap = false
            )
    )
    private char command_crafter$analyzeInlineTag(char original) {
        if(allowTag && command_crafter$analyzingResult != null && VanillaLanguage.Companion.isReaderInlineResources(reader)) {
            var isInlineTag = reader.canRead() && reader.peek() == '[';
            var startCursor = reader.getCursor();
            //noinspection unchecked
            var parsed = VanillaLanguage.Companion.analyzeRegistryTagTuple((DirectiveStringReader<AnalyzingResourceCreator>) reader, Registries.BLOCK, false, false, false);
            if(!isInlineTag) {
                parsed.getAnalyzingResult().getDiagnostics().removeIf(diagnostic -> diagnostic.getSeverity() == DiagnosticSeverity.Error);
                parsed.getAnalyzingResult().getSemanticTokens().clear();
            }
            command_crafter$analyzingResult.combineWith(parsed.getAnalyzingResult());
            if(isInlineTag) {
                tagId = parsed;
                return '#';
            }
            reader.setCursor(startCursor);
        }
        return original;
    }

    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/BlockArgumentParser;parseTagId()V"
            )
    )
    private void command_crafter$skipParseTagIdForInlineTags(BlockArgumentParser instance, Operation<Void> original) {
        if(allowTag && command_crafter$analyzingResult != null && tagId != null) {
            // Inline tag has already been parsed
            return;
        }
        original.call(instance);
    }

    @Inject(
            method = "parseTagProperties",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readString()Ljava/lang/String;",
                    ordinal = 0,
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void command_crafter$highlightTagPropertyName(CallbackInfo ci, @Local(ordinal = 1) int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @Inject(
            method = "parseTagProperties",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readString()Ljava/lang/String;",
                    ordinal = 1,
                    shift = At.Shift.AFTER,
                    remap = false
            )
    )
    private void command_crafter$highlightTagPropertyValue(CallbackInfo ci, @Local(ordinal = 0) int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @WrapOperation(
            method = "parseSnbt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readCompoundAsArgument(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private NbtCompound command_crafter$highlightNbtArg(StringReader reader, Operation<NbtCompound> op) throws CommandSyntaxException {
        if(command_crafter$analyzingResult == null)
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

        Decoder<?> decoder = null;
        if(blockState != null) {
            //noinspection unchecked
            var dataObjectDecoding = DataObjectDecoding.Companion.getForReader((DirectiveStringReader<AnalyzingResourceCreator>) reader);
            if (dataObjectDecoding != null)
                decoder = dataObjectDecoding.getDummyBlockEntityDecoders().get(blockState.getBlock());
        }

        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        )
                .withDiagnosticSeverity(DiagnosticSeverity.Warning)
                .analyzeFull(command_crafter$analyzingResult, true, decoder);
        return nbt instanceof NbtCompound ? (NbtCompound)nbt : null;
    }
}
