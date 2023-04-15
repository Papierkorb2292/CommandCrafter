package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.StringNbtReader;
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticTokensCreator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StringNbtReader.class)
public class StringNbtReaderMixin implements SemanticTokensCreator {

    @Shadow @Final private StringReader reader;
    private SemanticTokensBuilder command_crafter$semanticTokensBuilder = null;
    private int command_crafter$semanticCursorOffset;

    @Override
    public void command_crafter$setSemanticTokensBuilder(@NotNull SemanticTokensBuilder builder, int cursorOffset) {
        command_crafter$semanticTokensBuilder = builder;
        command_crafter$semanticCursorOffset = cursorOffset;
    }

    @ModifyReturnValue(
            method = "parseElementPrimitive",
            at = @At(
                    value = "RETURN"
            )
    )
    public NbtElement command_crafter$addSemanticsToPrimitive(NbtElement element, @Local int startCursor) {
        if(command_crafter$semanticTokensBuilder == null)
            return element;

        TokenType type;
        if(element instanceof NbtString) {
            type = TokenType.Companion.getSTRING();
        } else {
            var startChar = reader.getString().charAt(startCursor);
            type = startChar == 't' || startChar == 'f' ? TokenType.Companion.getENUM_MEMBER() : TokenType.Companion.getNUMBER();
        }
        command_crafter$semanticTokensBuilder.addAbsoluteMultiline(startCursor + command_crafter$semanticCursorOffset, reader.getCursor() - startCursor, type, 0);
        return element;
    }

    @ModifyVariable(
            method = "parseCompound",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;isEmpty()Z"
            )
    )
    private int command_crafter$addSemanticsToCompoundTag(int startCursor) {
        if(command_crafter$semanticTokensBuilder != null)
            command_crafter$semanticTokensBuilder.addAbsoluteMultiline(startCursor + command_crafter$semanticCursorOffset, reader.getCursor() - startCursor, TokenType.Companion.getPROPERTY(), 0);
        return startCursor;
    }

    @ModifyVariable(
            method = "parseElementPrimitiveArray",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;skipWhitespace()V",
                    remap = false
            )
    )
    private int command_crafter$addSemanticsToArrayType(int startCursor) {
        if(command_crafter$semanticTokensBuilder != null)
            command_crafter$semanticTokensBuilder.addAbsoluteMultiline(startCursor + command_crafter$semanticCursorOffset, 1, TokenType.Companion.getTYPE(), 0);
        return startCursor;
    }
}
