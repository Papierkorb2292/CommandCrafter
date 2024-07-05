package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.command.MacroInvocation;
import net.papierkorb2292.command_crafter.helper.IntList;
import net.papierkorb2292.command_crafter.parser.helper.MacroCursorMapperProvider;
import net.papierkorb2292.command_crafter.parser.helper.SplitProcessedInputCursorMapper;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(MacroInvocation.class)
public class MacroInvocationMixin implements MacroCursorMapperProvider {

    @Shadow @Final private List<String> variables;
    @Shadow @Final private List<String> segments;
    private IntList command_crafter$segmentCursorStarts;

    @Inject(
            method = "parse",
            at = @At("HEAD")
    )
    private static void command_crafter$createCursorMappingBuilder(String command, int lineNumber, CallbackInfoReturnable<MacroInvocation> cir, @Share("cursorStarts") LocalRef<IntList> cursorStartsRef) {
        cursorStartsRef.set(new IntList());
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

    @ModifyArg(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;substring(I)Ljava/lang/String;"
            )
    )
    private static int command_crafter$addLastSegmentCursorMapping(int beginIndex, @Share("cursorStarts") LocalRef<IntList> cursorStartsRef) {
        cursorStartsRef.get().add(beginIndex);
        return beginIndex;
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
            mapper.addMapping(segmentStart, currentCommandLength, segmentStart + segmentLen);
            currentCommandLength += segmentLen + variableLen;
        }
        if(segments.size() > variables.size()) {
            var lastSegmentIndex = segments.size() - 1;
            var segmentLength = segments.get(lastSegmentIndex).length();
            var segmentStart = command_crafter$segmentCursorStarts.get(lastSegmentIndex);
            mapper.addMapping(segmentStart, currentCommandLength, segmentStart + segmentLength);
        }

        return mapper;
    }
}
