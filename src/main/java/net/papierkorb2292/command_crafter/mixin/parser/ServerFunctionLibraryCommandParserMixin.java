package net.papierkorb2292.command_crafter.mixin.parser;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.ContextChain;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.ParseBox;
import net.minecraft.commands.ParseContext;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net/minecraft/server/ServerFunctionLibrary$1")
public class ServerFunctionLibraryCommandParserMixin {
    @Shadow
    @Final
    ParseBox val$commandParseBox;

    @Shadow
    @Final
    ParseContext val$compilationContext;

    @Inject(
            method = "parse",
            at = @At("HEAD"),
            cancellable = true
    )
    private void command_crafter$skipUnpackingForCustomFunctionParser(StringReader input, CallbackInfoReturnable<ContextChain<CommandSourceStack>> cir) {
        if(!VanillaLanguage.Companion.getCOMMAND_PARSER_OVERRIDE_CALLBACK().isBound())
            return;
        final var override = VanillaLanguage.Companion.getCOMMAND_PARSER_OVERRIDE_CALLBACK().get();
        override.invoke(val$commandParseBox, val$compilationContext);
        cir.setReturnValue(null);
    }
}
