package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStringReader.class)
public class ItemStringReaderMixin implements AnalyzingResultCreator {

    @Shadow @Final private StringReader reader;
    private AnalyzingResult command_crafter$analyzingResult = null;

    @Override
    public void command_crafter$setAnalyzingResult(@NotNull AnalyzingResult result) {
        command_crafter$analyzingResult = result;
    }

    @Inject(
            method = "readItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Identifier;fromCommandInput(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/util/Identifier;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$highlightItemId(CallbackInfo ci, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @ModifyExpressionValue(
            method = "readTag",
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
                    PackContentFileType.ITEM_TAGS_FILE_TYPE,
                    new StringRange(startCursor, reader.getCursor()),
                    command_crafter$analyzingResult,
                    (DirectiveStringReader<AnalyzingResourceCreator>) directiveStringReader
            );
        }
        return id;
    }

    @ModifyReceiver(
            method = "readNbt",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseCompound()Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private StringNbtReader command_crafter$analyzeNbt(StringNbtReader nbtReader) {
        if (command_crafter$analyzingResult != null) {
            ((AnalyzingResultCreator)nbtReader).command_crafter$setAnalyzingResult(command_crafter$analyzingResult);
        }
        return nbtReader;
    }
}
