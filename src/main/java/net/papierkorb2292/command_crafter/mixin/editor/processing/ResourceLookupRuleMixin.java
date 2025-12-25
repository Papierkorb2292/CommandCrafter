package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import net.minecraft.util.parsing.packrat.commands.ResourceLookupRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.resources.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ResourceLookupRule.class)
public class ResourceLookupRuleMixin implements PackContentFileTypeContainer {

    private PackContentFileType command_crafter$packContentFileType = null;
    private int startOffset = 0;

    @ModifyArg(
            method = "parse(Lnet/minecraft/util/parsing/packrat/ParseState;)Ljava/lang/Object;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/ResourceLookupRule;validateElement(Lcom/mojang/brigadier/ImmutableStringReader;Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;"
            )
    )
    private Identifier command_crafter$analyzeId(Identifier id, @Local(argsOnly = true) ParseState<StringReader> state, @Local int start) {
        var analyzingResultArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getAnalyzingResult());
        var offsetStart = start + startOffset;
        var stringReader = state.input();
        if(analyzingResultArg != null && stringReader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            var range = new StringRange(offsetStart, state.mark());
            if (command_crafter$packContentFileType != null) {
                //noinspection unchecked
                IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(id, command_crafter$packContentFileType, range, analyzingResultArg.getAnalyzingResult(), (DirectiveStringReader<AnalyzingResourceCreator>) directiveStringReader);
                return id;
            }
            analyzingResultArg.getAnalyzingResult().getSemanticTokens()
                    .addMultiline(range, TokenType.Companion.getPARAMETER(), 0);
        }
        return id;
    }

    protected void command_crafter$setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }

    @Override
    public void command_crafter$setPackContentFileType(@NotNull PackContentFileType packContentFileType) {
        command_crafter$packContentFileType = packContentFileType;
    }

    @Nullable
    @Override
    public PackContentFileType command_crafter$getPackContentFileType() {
        return command_crafter$packContentFileType;
    }
}
