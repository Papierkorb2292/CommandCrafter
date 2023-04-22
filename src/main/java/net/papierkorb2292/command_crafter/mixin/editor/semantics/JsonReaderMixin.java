package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.google.gson.stream.JsonReader;
import com.llamalad7.mixinextras.sugar.Local;
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticTokensCreator;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("unused")
@Mixin(value = JsonReader.class, remap = false)
public class JsonReaderMixin implements SemanticTokensCreator {

    @Shadow private int pos;
    @Shadow int peeked;
    @Shadow @Final private static int PEEKED_SINGLE_QUOTED_NAME;
    @Shadow @Final private static int PEEKED_DOUBLE_QUOTED_NAME;
    @Shadow @Final private static int PEEKED_SINGLE_QUOTED;
    @Shadow @Final private static int PEEKED_DOUBLE_QUOTED;
    @Shadow private String peekedString;
    @Shadow private long peekedLong;
    @Shadow @Final private static int PEEKED_UNQUOTED;
    @Shadow @Final private static int PEEKED_BUFFERED;
    @Shadow @Final private char[] buffer;
    @Shadow @Final private static int PEEKED_TRUE;
    @Shadow @Final private static int PEEKED_FALSE;
    @Shadow @Final private static int PEEKED_LONG;
    @Shadow @Final private static int PEEKED_NUMBER;
    private SemanticTokensBuilder command_crafter$semanticTokensBuilder = null;
    private int command_crafter$cursorOffset = 0;

    @Override
    public void command_crafter$setSemanticTokensBuilder(@NotNull SemanticTokensBuilder builder, int cursorOffset) {
        command_crafter$semanticTokensBuilder = builder;
        command_crafter$cursorOffset = cursorOffset;
    }

    private int command_crafter$getStringLength(String string) {
        if(peeked == PEEKED_SINGLE_QUOTED || peeked == PEEKED_DOUBLE_QUOTED || peeked == PEEKED_SINGLE_QUOTED_NAME || peeked == PEEKED_DOUBLE_QUOTED_NAME)
            return string.length() + 2;

        char c = buffer[pos - 1];
        if(c == '\'' || c == '"')
            return string.length() + 2;

        return string.length();


    }

    @Inject(
            method = "nextName",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/google/gson/stream/JsonReader;peeked:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void command_crafter$highlightName(CallbackInfoReturnable<String> cir, @Local String result) {
        if(command_crafter$semanticTokensBuilder == null)
            return;

        var length = command_crafter$getStringLength(result);
        command_crafter$semanticTokensBuilder.addAbsoluteMultiline(pos + command_crafter$cursorOffset - length, length, TokenType.Companion.getPROPERTY(), 0);
    }

    @Inject(
            method = "nextString",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/google/gson/stream/JsonReader;peeked:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void command_crafter$highlightString(CallbackInfoReturnable<String> cir, @Local String result) {
        if(command_crafter$semanticTokensBuilder == null
                || peeked == PEEKED_BUFFERED //The value was already highlighted                       hopefully
        )
            return;

        var length = command_crafter$getStringLength(result);
        command_crafter$semanticTokensBuilder.addAbsoluteMultiline(
                pos + command_crafter$cursorOffset - length,
                length,
                peeked == PEEKED_LONG || peeked == PEEKED_NUMBER
                    ? TokenType.Companion.getNUMBER()
                    : TokenType.Companion.getSTRING(),
                0
        );
    }

    @Inject(
            method = "nextDouble",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/google/gson/stream/JsonReader;peeked:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 1
            )
    )
    private void command_crafter$highlightNonLongDouble(CallbackInfoReturnable<String> cir) {
        if(command_crafter$semanticTokensBuilder == null
                || peeked == PEEKED_BUFFERED //The value was already highlighted                       hopefully
        )
            return;

        var length = command_crafter$getStringLength(peekedString);
        command_crafter$semanticTokensBuilder.addAbsoluteMultiline(pos + command_crafter$cursorOffset - length, length, TokenType.Companion.getNUMBER(), 0);
    }

    @Inject(
            method = { "nextInt", "nextLong" },
            at = @At(
                    value = "FIELD",
                    target = "Lcom/google/gson/stream/JsonReader;peeked:I",
                    opcode = Opcodes.PUTFIELD
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Ljava/lang/Integer;parseInt(Ljava/lang/String;)I"
                    ),
                    to = @At(
                            value = "INVOKE",
                            target = "Ljava/lang/Double;parseDouble(Ljava/lang/String;)D"
                    )
            )
    )
    private void command_crafter$highlightNonLongInt(CallbackInfoReturnable<String> cir) {
        if(command_crafter$semanticTokensBuilder == null
                || peeked == PEEKED_BUFFERED //The value was already highlighted                       hopefully
        )
            return;

        var length = command_crafter$getStringLength(peekedString);
        command_crafter$semanticTokensBuilder.addAbsoluteMultiline(pos + command_crafter$cursorOffset - length, length, TokenType.Companion.getNUMBER(), 0);
    }

    @Inject(
            method = { "nextDouble", "nextInt", "nextLong" },
            at = @At(
                    value = "RETURN",
                    ordinal = 0
            )
    )
    private void command_crafter$highlightPeekedLongNumbers(CallbackInfoReturnable<String> cir) {
        if(command_crafter$semanticTokensBuilder == null)
            return;

        var length = Long.toString(peekedLong).length();
        command_crafter$semanticTokensBuilder.addAbsoluteMultiline(pos + command_crafter$cursorOffset - length, length, TokenType.Companion.getNUMBER(), 0);
    }

    @Inject(
            method = "nextBoolean",
            at = @At(
                    value = "CONSTANT",
                    args = "intValue=5"
            )
    )
    private void command_crafter$highlightBooleans(CallbackInfoReturnable<Boolean> cir) {
        if(command_crafter$semanticTokensBuilder == null)
            return;

        int length;
        if(peeked == PEEKED_TRUE)
            length = 4;
        else if(peeked == PEEKED_FALSE)
            length = 5;
        else return;

        command_crafter$semanticTokensBuilder.addAbsoluteMultiline(pos + command_crafter$cursorOffset - length, length, TokenType.Companion.getENUM_MEMBER(), 0);
    }

    @Inject(
            method = "nextNull",
            at = @At("RETURN")
    )
    private void command_crafter$highlightNull(CallbackInfo ci) {
        if(command_crafter$semanticTokensBuilder == null)
            return;

        command_crafter$semanticTokensBuilder.addAbsoluteMultiline(pos + command_crafter$cursorOffset - 4, 4, TokenType.Companion.getKEYWORD(), 0);
    }
}
