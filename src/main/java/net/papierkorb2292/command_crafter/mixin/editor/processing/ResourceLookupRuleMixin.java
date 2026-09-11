package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import net.minecraft.resources.Identifier;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.commands.ResourceLookupRule;
import net.papierkorb2292.command_crafter.editor.processing.IdArgumentTypeAnalyzer;
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackContentFileTypeContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

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
        IdArgumentTypeAnalyzer.INSTANCE.analyzePackrat(id, start + startOffset, state.input(), command_crafter$packContentFileType);
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
