package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Decoder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.Identifier;
import net.papierkorb2292.command_crafter.MixinUtil;
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

@Mixin(BlockStateParser.class)
public class BlockStateParserMixin implements AnalyzingResultCreator {

    @Shadow @Final private StringReader reader;
    @Shadow private @Nullable HolderSet<Block> tag;
    @Shadow @Final private boolean forTesting;
    @Shadow private @Nullable BlockState state;
    private AnalyzingResult command_crafter$analyzingResult;

    @Override
    public void command_crafter$setAnalyzingResult(@Nullable AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Inject(
            method = "readBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/Identifier;read(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/resources/Identifier;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$highlightBlockId(CallbackInfo ci, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @Inject(
            method = "readProperties",
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
            method = "readProperties",
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
            method = "readTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/Identifier;read(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/resources/Identifier;"
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
        if(forTesting && command_crafter$analyzingResult != null && VanillaLanguage.Companion.isReaderInlineResources(reader)) {
            var isInlineTag = reader.canRead() && reader.peek() == '[';
            var startCursor = reader.getCursor();
            //noinspection unchecked
            var parsed = VanillaLanguage.Companion.analyzeRegistryTagTuple((DirectiveStringReader<AnalyzingResourceCreator>) reader, BuiltInRegistries.BLOCK, false, false, false);
            if(!isInlineTag) {
                parsed.getAnalyzingResult().getDiagnostics().removeIf(diagnostic -> diagnostic.getSeverity() == DiagnosticSeverity.Error);
                parsed.getAnalyzingResult().getSemanticTokens().clear();
            }
            command_crafter$analyzingResult.combineWith(parsed.getAnalyzingResult());
            if(isInlineTag) {
                tag = parsed;
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
                    target = "Lnet/minecraft/commands/arguments/blocks/BlockStateParser;readTag()V"
            )
    )
    private void command_crafter$skipParseTagIdForInlineTags(BlockStateParser instance, Operation<Void> original) {
        if(forTesting && command_crafter$analyzingResult != null && tag != null) {
            // Inline tag has already been parsed
            return;
        }
        original.call(instance);
    }

    @Inject(
            method = "readVagueProperties",
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
            method = "readVagueProperties",
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
            method = "readNbt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/TagParser;parseCompoundAsArgument(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/nbt/CompoundTag;"
            )
    )
    private CompoundTag command_crafter$highlightNbtArg(StringReader reader, Operation<CompoundTag> op) throws CommandSyntaxException {
        if(command_crafter$analyzingResult == null)
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

        Decoder<?> decoder = null;
        if(state != null) {
            //noinspection unchecked
            var dataObjectDecoding = DataObjectDecoding.Companion.getForReader((DirectiveStringReader<AnalyzingResourceCreator>) reader);
            if (dataObjectDecoding != null)
                decoder = dataObjectDecoding.getDummyBlockEntityDecoders().get(state.getBlock());
        }

        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        )
                .withDiagnosticSeverity(DiagnosticSeverity.Warning)
                .analyzeFull(command_crafter$analyzingResult, true, decoder);
        return nbt instanceof CompoundTag ? (CompoundTag)nbt : null;
    }

    @WrapMethod(
            method = {"readVagueProperties", "readProperties"}
    )
    private void command_crafter$allowMalformedProperties(Operation<Void> op) {
        if(command_crafter$analyzingResult == null) {
            op.call();
            return;
        }
        try {
            MixinUtil.<Void, CommandSyntaxException>callWithThrows(op);
        } catch (CommandSyntaxException e) {
            // Skip to next property
            while(reader.canRead() && reader.peek() != ',' && reader.peek() != ']')
                reader.skip();
            if(!reader.canRead())
                return;

            // Go one back, because the beginning of the parser method will skip a char (the comma is supposed to be skipped)
            if(reader.peek() == ']')
                reader.setCursor(reader.getCursor() - 1);
            // Invoke the parser again
            command_crafter$allowMalformedProperties(op);
        }
    }
}
