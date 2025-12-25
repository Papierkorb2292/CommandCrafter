package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.datafixers.util.Either;
import net.minecraft.util.parsing.packrat.commands.ResourceLookupRule;
import net.minecraft.resources.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Optional;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(ResourceLookupRule.class)
public class ResourceLookupRuleMixin<C, V> {

    @ModifyArg(
            method = "parse(Lnet/minecraft/util/parsing/packrat/ParseState;)Ljava/lang/Object;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/parsing/packrat/commands/ResourceLookupRule;validateElement(Lcom/mojang/brigadier/ImmutableStringReader;Lnet/minecraft/resources/Identifier;)Ljava/lang/Object;"
            )
    )
    private Identifier command_crafter$unparseId(Identifier id, @Local int start) {
        var unparsingListArg = getOrNull(PackratParserAdditionalArgs.INSTANCE.getStringifiedArgument());
        if(unparsingListArg != null) {
            unparsingListArg.getStringified().add(Either.left(id.toString()));
        }
        return id;
    }
}
