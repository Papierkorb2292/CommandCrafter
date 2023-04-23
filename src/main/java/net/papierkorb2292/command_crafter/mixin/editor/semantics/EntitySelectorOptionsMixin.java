package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.command.EntitySelectorOptions;
import net.minecraft.command.EntitySelectorReader;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticBuilderContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticTokensCreator;
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
            var tokensBuilder = ((SemanticBuilderContainer)selectorReader).command_crafter$getSemanticTokensBuilder();
            if(tokensBuilder != null) {
                tokensBuilder.addAbsoluteMultiline(
                        cursor + ((SemanticBuilderContainer)selectorReader).command_crafter$getCursorOffset(),
                        selectorReader.getReader().getCursor() - cursor,
                        tokenType,
                        0
                );
            }
        };
    }

    @ModifyReceiver(
            method = "method_9966",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseCompound()Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private static StringNbtReader command_crafter$highlightNbtOption(StringNbtReader nbtReader, EntitySelectorReader selectorReader) {
        var semanticBuilderContainer = (SemanticBuilderContainer)selectorReader;
        var tokenBuilder = semanticBuilderContainer.command_crafter$getSemanticTokensBuilder();
        if(tokenBuilder != null) {
            ((SemanticTokensCreator)nbtReader).command_crafter$setSemanticTokensBuilder(
                    tokenBuilder,
                    semanticBuilderContainer.command_crafter$getCursorOffset()
            );
        }
        return nbtReader;
    }

    @ModifyExpressionValue(
            method = "method_9975",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readUnquotedString()Ljava/lang/String;"
            )
    )
    private static String command_crafter$highlightScoreOptionScoreboardName(String name, EntitySelectorReader selectorReader) {
        var tokensBuilder = ((SemanticBuilderContainer)selectorReader).command_crafter$getSemanticTokensBuilder();
        if(tokensBuilder != null) {
            tokensBuilder.addAbsoluteMultiline(
                    selectorReader.getReader().getCursor() - name.length() + ((SemanticBuilderContainer)selectorReader).command_crafter$getCursorOffset(),
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
    private static void command_crafter$storeScoreOptionNumberCursor(EntitySelectorReader reader, CallbackInfo ci, @Share("numberCursor") LocalIntRef numberCursor) {
        numberCursor.set(reader.getReader().getCursor());
    }

    @Inject(
            method = "method_9975",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/predicate/NumberRange$IntRange;parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/predicate/NumberRange$IntRange;",
                    shift = At.Shift.AFTER
            )
    )
    private static void command_crafter$highlightScoreOptionNumber(EntitySelectorReader selectorReader, CallbackInfo ci, @Share("numberCursor") LocalIntRef numberCursor) {
        var tokensBuilder = ((SemanticBuilderContainer)selectorReader).command_crafter$getSemanticTokensBuilder();
        if(tokensBuilder != null) {
            tokensBuilder.addAbsoluteMultiline(
                    numberCursor.get() + ((SemanticBuilderContainer)selectorReader).command_crafter$getCursorOffset(),
                    selectorReader.getReader().getCursor() - numberCursor.get(),
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
    private static void command_crafter$storeAdvancementOptionIdentifierCursor(EntitySelectorReader reader, CallbackInfo ci, @Share("startCursor") LocalIntRef startCursor) {
        startCursor.set(reader.getReader().getCursor());
    }

    @ModifyExpressionValue(
            method = "method_9974",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Identifier;fromCommandInput(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/util/Identifier;"
            )
    )
    private static Identifier command_crafter$highlightAdvancementOptionIdentifier(Identifier id, EntitySelectorReader selectorReader, @Share("startCursor") LocalIntRef startCursor) {
        var tokensBuilder = ((SemanticBuilderContainer)selectorReader).command_crafter$getSemanticTokensBuilder();
        if(tokensBuilder != null) {
            tokensBuilder.addAbsoluteMultiline(
                    startCursor.get() + ((SemanticBuilderContainer)selectorReader).command_crafter$getCursorOffset(),
                    selectorReader.getReader().getCursor() - startCursor.get(),
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
            )
    )
    private static String command_crafter$highlightAdvancementOptionCriterionName(String name, EntitySelectorReader selectorReader) {
        var tokensBuilder = ((SemanticBuilderContainer)selectorReader).command_crafter$getSemanticTokensBuilder();
        if(tokensBuilder != null) {
            tokensBuilder.addAbsoluteMultiline(
                    selectorReader.getReader().getCursor() - name.length() + ((SemanticBuilderContainer)selectorReader).command_crafter$getCursorOffset(),
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
            )
    )
    private static boolean command_crafter$highlightAdvancementOptionBoolean(boolean value, EntitySelectorReader selectorReader) {
        var tokensBuilder = ((SemanticBuilderContainer)selectorReader).command_crafter$getSemanticTokensBuilder();
        if(tokensBuilder != null) {
            var length = value ? 4 : 5;
            tokensBuilder.addAbsoluteMultiline(
                    selectorReader.getReader().getCursor() - length + ((SemanticBuilderContainer)selectorReader).command_crafter$getCursorOffset(),
                    length,
                    TokenType.Companion.getENUM_MEMBER(),
                    0
            );
        }
        return value;
    }
}
