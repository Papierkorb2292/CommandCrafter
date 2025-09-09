package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.command.MacroInvocation;
import net.papierkorb2292.command_crafter.helper.IntList;
import net.papierkorb2292.command_crafter.parser.helper.MacroCursorMapperProvider;
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(MacroInvocation.class)
public class MacroInvocationMixin implements MacroCursorMapperProvider {

    @Shadow @Final private List<String> variables;
    @Shadow @Final private List<String> segments;
    private IntList command_crafter$segmentCursorStarts;

    @Inject(
            method = "parse",
            at = @At("HEAD")
    )
    private static void command_crafter$setupLocalRefs(String command, CallbackInfoReturnable<MacroInvocation> cir, @Share("cursorStarts") LocalRef<IntList> cursorStartsRef, @Share("allowMalformed") LocalBooleanRef allowMalformedRef) {
        cursorStartsRef.set(new IntList());

        final var allowMalformed = getOrNull(VanillaLanguage.Companion.getALLOW_MALFORMED_MACRO());
        if(allowMalformed != null && allowMalformed)
            allowMalformedRef.set(true);
    }

    @ModifyArg(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;substring(II)Ljava/lang/String;",
                    ordinal = 0
            ),
            index = 0
    )
    private static int command_crafter$addSegmentBeforeVarCursorMapping(int beginIndex, @Share("cursorStarts") LocalRef<IntList> cursorStartsRef) {
        cursorStartsRef.get().add(beginIndex);
        return beginIndex;
    }

    @Inject(
            method = "parse",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/util/List;Ljava/util/List;)Lnet/minecraft/command/MacroInvocation;"
            )
    )
    private static void command_crafter$addLastSegmentCursorMapping(String command, CallbackInfoReturnable<MacroInvocation> cir, @Local(ordinal = 1) int beginIndex, @Share("cursorStarts") LocalRef<IntList> cursorStartsRef) {
        cursorStartsRef.get().add(beginIndex);
    }

    @ModifyReturnValue(
            method = "parse",
            at = @At("RETURN")
    )
    private static MacroInvocation command_crafter$addCursorMappingToInstance(MacroInvocation macroInvocation, @Share("cursorStarts") LocalRef<IntList> cursorStartsRef) {
        ((MacroInvocationMixin)(Object)macroInvocation).command_crafter$segmentCursorStarts = cursorStartsRef.get();
        return macroInvocation;
    }

    @NotNull
    @Override
    public SplitProcessedInputCursorMapper command_crafter$getCursorMapper(@NotNull List<String> arguments) {
        var mapper = new SplitProcessedInputCursorMapper();

        var currentCommandLength = 0;
        for(int i = 0; i < variables.size(); i++) {
            var variableLen = arguments.get(i).length();
            var segmentLen = segments.get(i).length();
            var segmentStart = command_crafter$segmentCursorStarts.get(i);
            mapper.addMapping(segmentStart, currentCommandLength, segmentLen);
            currentCommandLength += segmentLen + variableLen;
        }
        // Add mapper for anything after the last variable, even if there is just an empty string there
        var lastSegmentIndex = command_crafter$segmentCursorStarts.getSize() - 1;
        var segmentLength = lastSegmentIndex < segments.size() ? segments.get(lastSegmentIndex).length() : 0;
        var segmentStart = command_crafter$segmentCursorStarts.get(lastSegmentIndex);
        mapper.addMapping(segmentStart, currentCommandLength, segmentLength);

        return mapper;
    }

    @ModifyExpressionValue(
            method = "parse",
            at = @At(
                    value = "INVOKE:FIRST",
                    target = "Ljava/lang/String;indexOf(II)I"
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=41" // ')'
                    )
            )
    )
    private static int command_crafter$allowMalformedUnterminatedMacroVariable(int macroEnd, String command, @Local(ordinal = 2) int k, @Share("allowMalformed") LocalBooleanRef allowMalformedRef) {
        if(!allowMalformedRef.get())
            return macroEnd;
        if(macroEnd == -1)
            macroEnd = command.length();
        var adjustedEndCursor = k;
        while(adjustedEndCursor != macroEnd && !Character.isWhitespace(command.charAt(adjustedEndCursor)))
            adjustedEndCursor++;
        return adjustedEndCursor;
    }

    @ModifyExpressionValue(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/MacroInvocation;isValidMacroName(Ljava/lang/String;)Z"
            )
    )
    private static boolean command_crafter$allowMalformedMacroName(boolean isValid, @Share("allowMalformed") LocalBooleanRef allowMalformedRef) {
        if(!allowMalformedRef.get())
            return isValid;

        return true;
    }

    @Definition(id = "j", local = @Local(ordinal = 1, type=int.class))
    @Expression("j")
    @ModifyExpressionValue(
            method = "parse",
            at = @At("MIXINEXTRAS:EXPRESSION:LAST"),
            slice = @Slice(
                    to = @At(
                            value = "CONSTANT",
                            args = "stringValue=No variables in macro"
                    )
            )
    )
    private static int command_crafter$allowMalformedNoVariables(int lastMacroEnd, @Share("allowMalformed") LocalBooleanRef allowMalformedRef) {
        if(!allowMalformedRef.get())
            return lastMacroEnd;
        return -1;
    }

    @Definition(id = "j", local = @Local(ordinal = 1, type=int.class))
    @Expression("j")
    @ModifyExpressionValue(
            method = "parse",
            at = @At("MIXINEXTRAS:EXPRESSION:LAST")
    )
    private static int command_crafter$allowMalformedFixErrorOnMissingClosingParenthesesAtEnd(int lastMacroEnd, String command, @Share("allowMalformed")LocalBooleanRef allowMalformedRef) {
        if(!allowMalformedRef.get())
            return lastMacroEnd;
        return Math.min(lastMacroEnd, command.length());
    }
}
