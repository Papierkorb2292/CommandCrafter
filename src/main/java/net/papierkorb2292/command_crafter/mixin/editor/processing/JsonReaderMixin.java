package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.gson.stream.JsonReader;
import com.llamalad7.mixinextras.sugar.Local;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import org.jetbrains.annotations.Nullable;
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
public class JsonReaderMixin implements AnalyzingResultCreator {

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

    private AnalyzingResult command_crafter$analyzingResult = null;
    private int command_crafter$lastPeekStart = 0;
    private int command_crafter$consumedChars = 0;

    @Override
    public void command_crafter$setAnalyzingResult(@Nullable AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Inject(
            method = "fillBuffer",
            at = @At("HEAD")
    )
    private void command_crafter$addConsumedChars(CallbackInfoReturnable<Integer> cir) {
        command_crafter$consumedChars += pos;
    }

    @Inject(
            method = "doPeek",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/gson/stream/JsonReader;nextNonWhitespace(Z)I"
            )
    )
    private void command_crafter$setLastPeekStart(CallbackInfoReturnable<Integer> cir) {
        command_crafter$lastPeekStart = pos + command_crafter$consumedChars;
    }

    private void command_crafter$highlightSinceLastPeek(TokenType tokenType) {
        if(command_crafter$analyzingResult == null)
            return;
        var length = pos + command_crafter$consumedChars - command_crafter$lastPeekStart;
        command_crafter$analyzingResult.getSemanticTokens().addMultiline(command_crafter$lastPeekStart, length, tokenType, 0);
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
        command_crafter$highlightSinceLastPeek(TokenType.Companion.getPARAMETER());
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
        if(peeked == PEEKED_BUFFERED)
            //The value was already highlighted                       hopefully
            return;

        command_crafter$highlightSinceLastPeek(
                peeked == PEEKED_LONG || peeked == PEEKED_NUMBER
                    ? TokenType.Companion.getNUMBER()
                    : TokenType.Companion.getSTRING()
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
        if(peeked == PEEKED_BUFFERED)
            //The value was already highlighted                       hopefully
            return;

        command_crafter$highlightSinceLastPeek(TokenType.Companion.getNUMBER());
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
        if(peeked == PEEKED_BUFFERED)
            //The value was already highlighted                       hopefully
            return;

        command_crafter$highlightSinceLastPeek(TokenType.Companion.getNUMBER());
    }

    @Inject(
            method = { "nextDouble", "nextInt", "nextLong" },
            at = @At(
                    value = "RETURN",
                    ordinal = 0
            )
    )
    private void command_crafter$highlightPeekedLongNumbers(CallbackInfoReturnable<String> cir) {
        command_crafter$highlightSinceLastPeek(TokenType.Companion.getNUMBER());
    }

    @Inject(
            method = "nextBoolean",
            at = @At(
                    value = "CONSTANT",
                    args = "intValue=5"
            )
    )
    private void command_crafter$highlightBooleans(CallbackInfoReturnable<Boolean> cir) {
        command_crafter$highlightSinceLastPeek(TokenType.Companion.getENUM_MEMBER());
    }

    @Inject(
            method = "nextNull",
            at = @At("RETURN")
    )
    private void command_crafter$highlightNull(CallbackInfo ci) {
        command_crafter$highlightSinceLastPeek(TokenType.Companion.getKEYWORD());
    }
}
