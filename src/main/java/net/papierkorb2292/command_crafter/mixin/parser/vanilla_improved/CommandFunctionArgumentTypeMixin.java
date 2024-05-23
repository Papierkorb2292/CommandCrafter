package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.CommandFunctionArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.RawResourceFunctionArgument;
import net.papierkorb2292.command_crafter.parser.helper.SourceAware;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(CommandFunctionArgumentType.class)
public class CommandFunctionArgumentTypeMixin implements SourceAware, StringifiableArgumentType {

    private CommandSource command_crafter$commandSource;

    @Inject(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/CommandFunctionArgumentType$FunctionArgument;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void command_crafter$parseFunctionInlining(StringReader reader, CallbackInfoReturnable<CommandFunctionArgumentType.FunctionArgument> cir) {
        if(!reader.canRead() || !VanillaLanguage.Companion.isReaderInlineResources(reader) || command_crafter$commandSource == null) {
            return;
        }
        var directiveStringReader = (DirectiveStringReader<?>) reader;
        var copiedReader = directiveStringReader.copy();
        var argument = VanillaLanguage.Companion.parseImprovedFunctionReference(copiedReader, command_crafter$commandSource);
        directiveStringReader.skipTo(copiedReader);
        if(argument != null) {
            cir.setReturnValue(argument);
        }
    }

    @Override
    public void command_crafter$setCommandSource(@NotNull CommandSource source) {
        command_crafter$commandSource = source;
    }

    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<ServerCommandSource> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) {
        var argument = context.getArgument(name, CommandFunctionArgumentType.FunctionArgument.class);
        if (!(argument instanceof RawResourceFunctionArgument resourceArgument)) {
            return null;
        }
        if(resourceArgument.isTag()) {
            return List.of(Either.left("#"), Either.right(resourceArgument.getResource()));
        }
        return Collections.singletonList(Either.right(resourceArgument.getResource()));
    }
}
