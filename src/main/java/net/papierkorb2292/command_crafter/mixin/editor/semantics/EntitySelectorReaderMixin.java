package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.EntitySelectorReader;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySelectorReader.class)
public class EntitySelectorReaderMixin implements AnalyzingResultDataContainer {

    @Shadow @Final private StringReader reader;
    private AnalyzingResult command_crafter$analyzingResult = null;

    @Override
    public void command_crafter$setAnalyzingResult(@NotNull AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Override
    public AnalyzingResult command_crafter$getAnalyzingResult() {
        return command_crafter$analyzingResult;
    }

    @Inject(
            method = "readArguments",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;getHandler(Lnet/minecraft/command/EntitySelectorReader;Ljava/lang/String;I)Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;"
            )
    )
    private void command_crafter$highlightOptionName(CallbackInfo ci, @Local String name, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addAbsoluteMultiline(startCursor, name.length(), TokenType.Companion.getPROPERTY(), 0);
        }
    }

    @Inject(
            method = "readAtVariable",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
                    ordinal = 1,
                    remap = false
            )
    )
    private void command_crafter$highlightAt(CallbackInfo ci) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addAbsoluteMultiline(reader.getCursor() - 2, 2, TokenType.Companion.getCLASS(), 0);
        }
    }

    @Inject(
            method = "readRegular",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/UUID;fromString(Ljava/lang/String;)Ljava/util/UUID;"
            )
    )
    private void command_crafter$highlightRegular(CallbackInfo ci, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addAbsoluteMultiline(startCursor, reader.getCursor(), TokenType.Companion.getPARAMETER(), 0);
        }
    }
}
