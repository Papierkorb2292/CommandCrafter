package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.EntitySelectorReader;
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.SemanticBuilderContainer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySelectorReader.class)
public class EntitySelectorReaderMixin implements SemanticBuilderContainer {

    @Shadow @Final private StringReader reader;
    private SemanticTokensBuilder command_crafter$semanticTokensBuilder = null;
    private int command_crafter$cursorOffset = 0;

    @Override
    public void command_crafter$setSemanticTokensBuilder(@NotNull SemanticTokensBuilder builder, int cursorOffset) {
        command_crafter$semanticTokensBuilder = builder;
        command_crafter$cursorOffset = cursorOffset;
    }

    @Override
    public SemanticTokensBuilder command_crafter$getSemanticTokensBuilder() {
        return command_crafter$semanticTokensBuilder;
    }

    @Override
    public int command_crafter$getCursorOffset() {
        return command_crafter$cursorOffset;
    }

    @Inject(
            method = "readArguments",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/EntitySelectorOptions;getHandler(Lnet/minecraft/command/EntitySelectorReader;Ljava/lang/String;I)Lnet/minecraft/command/EntitySelectorOptions$SelectorHandler;"
            )
    )
    private void command_crafter$highlightOptionName(CallbackInfo ci, @Local String name, @Local int startCursor) {
        if(command_crafter$semanticTokensBuilder != null) {
            command_crafter$semanticTokensBuilder.addAbsoluteMultiline(startCursor + command_crafter$cursorOffset, name.length(), TokenType.Companion.getPROPERTY(), 0);
        }
    }

    @Inject(
            method = "readAtVariable",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
                    ordinal = 1
            )
    )
    private void command_crafter$highlightAt(CallbackInfo ci) {
        if(command_crafter$semanticTokensBuilder != null) {
            command_crafter$semanticTokensBuilder.addAbsoluteMultiline(reader.getCursor() - 2 + command_crafter$cursorOffset, 2, TokenType.Companion.getCLASS(), 0);
        }
    }
}
