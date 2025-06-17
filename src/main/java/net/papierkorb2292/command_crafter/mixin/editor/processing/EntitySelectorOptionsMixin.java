package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.EntitySelectorOptions;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.papierkorb2292.command_crafter.editor.processing.*;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.helper.AnalyzedRegistryEntryList;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("unused")
@Mixin(EntitySelectorOptions.class)
public class EntitySelectorOptionsMixin {

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=x"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightXOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=y"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightYOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=z"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightZOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=dx"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightDXOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=dy"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightDYOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=dz"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightDZOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=distance"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightDistanceOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=level"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightLevelOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=limit"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightLimitOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=x_rotation"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightXRotationOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=y_rotation"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightYRotationOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getNUMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=name"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightNameOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getSTRING());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=team"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightTeamOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getSTRING());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=tag"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightTagOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getSTRING());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=sort"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightSortOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getENUM_MEMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=gamemode"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightGamemodeOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getENUM_MEMBER());
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=type"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightTypeOption(EntitySelectorOptions.SelectorHandler handler) {
        return selectorReader -> {
            var analyzingResult = ((AnalyzingResultDataContainer)selectorReader).command_crafter$getAnalyzingResult();
            var reader = selectorReader.getReader();
            if(analyzingResult == null || !(reader instanceof DirectiveStringReader<?> directiveReader) || !(directiveReader.getResourceCreator() instanceof AnalyzingResourceCreator)) {
                handler.handle(selectorReader);
                return;
            }
            var hasNegationChar = false;
            var maxEndCursor = reader.getCursor();
            if(reader.canRead()) {
                if(reader.peek() == '!') {
                    hasNegationChar = true;
                    reader.skip();
                }
                var startCursor = reader.getCursor();
                var isTag = reader.canRead() && reader.peek() == '#';
                if(isTag) reader.skip();
                try {
                    var tagId = Identifier.fromCommandInput(reader);
                    if(isTag) {
                        //noinspection unchecked
                        IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(
                                tagId,
                                PackContentFileType.ENTITY_TYPE_TAGS_FILE_TYPE,
                                new StringRange(startCursor, reader.getCursor()),
                                analyzingResult,
                                (DirectiveStringReader<AnalyzingResourceCreator>) reader
                        );
                    } else {
                        analyzingResult.getSemanticTokens().addMultiline(
                                startCursor,
                                reader.getCursor() - startCursor,
                                TokenType.Companion.getPARAMETER(),
                                0
                        );
                    }
                } catch(InvalidIdentifierException ignored) { }
                maxEndCursor = reader.getCursor();
                reader.setCursor(startCursor);
            }
            if(VanillaLanguage.Companion.isReaderInlineResources(reader)) {
                var isInlineTag = reader.canRead() && reader.peek() == '[';
                //noinspection unchecked
                var parsed = VanillaLanguage.Companion.analyzeRegistryTagTuple((DirectiveStringReader<AnalyzingResourceCreator>) reader, Registries.ENTITY_TYPE, false, hasNegationChar, true);
                if(parsed instanceof AnalyzedRegistryEntryList<EntityType<?>> analyzed) {
                    if(!isInlineTag) {
                        parsed.getAnalyzingResult().getDiagnostics().removeIf(diagnostic -> diagnostic.getSeverity() == DiagnosticSeverity.Error);
                        parsed.getAnalyzingResult().getSemanticTokens().clear();
                    }
                    analyzingResult.combineWith(analyzed.getAnalyzingResult());
                }
                if(reader.getCursor() > maxEndCursor)
                    maxEndCursor = reader.getCursor();
            }
            reader.setCursor(maxEndCursor);
        };
    }

    @ModifyArg(
            method = "register",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;putOption(Ljava/lang/String;Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;Ljava/util/function/Predicate;Lnet/minecraft/text/Text;)V",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=predicate"
                    )
            )
    )
    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightPredicateOption(EntitySelectorOptions.SelectorHandler handler) {
        return command_crafter$highlightFullOption(handler, TokenType.Companion.getPARAMETER());
    }

    private static EntitySelectorOptions.SelectorHandler command_crafter$highlightFullOption(EntitySelectorOptions.SelectorHandler handler, TokenType tokenType) {
        return selectorReader -> {
            var cursor = selectorReader.getReader().getCursor();
            handler.handle(selectorReader);
            var analyzingResult = ((AnalyzingResultDataContainer)selectorReader).command_crafter$getAnalyzingResult();
            if(analyzingResult != null) {
                analyzingResult.getSemanticTokens().addMultiline(
                        cursor,
                        selectorReader.getReader().getCursor() - cursor,
                        tokenType,
                        0
                );
            }
        };
    }

    @WrapOperation(
            method = "method_9966",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readCompoundAsArgument(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private static NbtCompound command_crafter$highlightNbtOption(StringReader reader, Operation<NbtCompound> op, EntitySelectorReader selectorReader) throws CommandSyntaxException {
        var analyzingResult = ((AnalyzingResultDataContainer)selectorReader).command_crafter$getAnalyzingResult();
        if(analyzingResult == null)
            return op.call(reader);
        //noinspection unchecked
        var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>) selectorReader.getReader();
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

    @ModifyExpressionValue(
            method = "method_9975",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readUnquotedString()Ljava/lang/String;"
            ),
            remap = false
    )
    private static String command_crafter$highlightScoreOptionScoreboardName(String name, EntitySelectorReader selectorReader) {
        var analyzingResult = ((AnalyzingResultDataContainer)selectorReader).command_crafter$getAnalyzingResult();
        if(analyzingResult != null) {
            analyzingResult.getSemanticTokens().addMultiline(
                    selectorReader.getReader().getCursor() - name.length(),
                    name.length(),
                    TokenType.Companion.getPROPERTY(),
                    0
            );
        }
        return name;
    }

    @Inject(
            method = "method_9975",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/predicate/NumberRange$IntRange;parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/predicate/NumberRange$IntRange;"
            )
    )
    private static void command_crafter$storeScoreOptionNumberCursor(EntitySelectorReader reader, CallbackInfo ci, @Share("numberCursor") LocalIntRef numberCursorRef) {
        numberCursorRef.set(reader.getReader().getCursor());
    }

    @Inject(
            method = "method_9975",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/predicate/NumberRange$IntRange;parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/predicate/NumberRange$IntRange;",
                    shift = At.Shift.AFTER
            )
    )
    private static void command_crafter$highlightScoreOptionNumber(EntitySelectorReader selectorReader, CallbackInfo ci, @Share("numberCursor") LocalIntRef numberCursorRef) {
        var analyzingResult = ((AnalyzingResultDataContainer)selectorReader).command_crafter$getAnalyzingResult();
        if(analyzingResult != null) {
            analyzingResult.getSemanticTokens().addMultiline(
                    numberCursorRef.get(),
                    selectorReader.getReader().getCursor() - numberCursorRef.get(),
                    TokenType.Companion.getNUMBER(),
                    0
            );
        }
    }

    @Inject(
            method = "method_9974",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Identifier;fromCommandInput(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/util/Identifier;"
            )
    )
    private static void command_crafter$storeAdvancementOptionIdentifierCursor(EntitySelectorReader reader, CallbackInfo ci, @Share("startCursor") LocalIntRef startCursorRef) {
        startCursorRef.set(reader.getReader().getCursor());
    }

    @ModifyExpressionValue(
            method = "method_9974",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Identifier;fromCommandInput(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier command_crafter$highlightAdvancementOptionIdentifier(Identifier id, EntitySelectorReader selectorReader, @Share("startCursor") LocalIntRef startCursorRef) {
        var analyzingResult = ((AnalyzingResultDataContainer)selectorReader).command_crafter$getAnalyzingResult();
        if(analyzingResult != null) {
            analyzingResult.getSemanticTokens().addMultiline(
                    startCursorRef.get(),
                    selectorReader.getReader().getCursor() - startCursorRef.get(),
                    TokenType.Companion.getPROPERTY(),
                    0
            );
        }
        return id;
    }

    @ModifyExpressionValue(
            method = "method_9974",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readUnquotedString()Ljava/lang/String;"
            ),
            remap = false
    )
    private static String command_crafter$highlightAdvancementOptionCriterionName(String name, EntitySelectorReader selectorReader) {
        var analyzingResult = ((AnalyzingResultDataContainer)selectorReader).command_crafter$getAnalyzingResult();
        if(analyzingResult != null) {
            analyzingResult.getSemanticTokens().addMultiline(
                    selectorReader.getReader().getCursor() - name.length(),
                    name.length(),
                    TokenType.Companion.getPROPERTY(),
                    0
            );
        }
        return name;
    }

    @ModifyExpressionValue(
            method = "method_9974",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readBoolean()Z"
            ),
            remap = false
    )
    private static boolean command_crafter$highlightAdvancementOptionBoolean(boolean value, EntitySelectorReader selectorReader) {
        var analyzingResult = ((AnalyzingResultDataContainer)selectorReader).command_crafter$getAnalyzingResult();
        if(analyzingResult != null) {
            var length = value ? 4 : 5;
            analyzingResult.getSemanticTokens().addMultiline(
                    selectorReader.getReader().getCursor() - length,
                    length,
                    TokenType.Companion.getENUM_MEMBER(),
                    0
            );
        }
        return value;
    }
}
