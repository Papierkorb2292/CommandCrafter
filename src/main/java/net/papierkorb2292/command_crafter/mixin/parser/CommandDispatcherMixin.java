package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.helper.DirectiveStringReaderConsumer;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("unused")
@Mixin(CommandDispatcher.class)
public class CommandDispatcherMixin {
    @Shadow @Final public static char ARGUMENT_SEPARATOR_CHAR;

    @ModifyExpressionValue(
            method = "parseNodes",
            at = @At(
                value = "INVOKE",
                target = "Lcom/mojang/brigadier/StringReader;peek()C"
            )
    )
    private char command_crafter$allowMultilineLiteralSeparator(char c, @Local(name = "reader") StringReader reader) {
        if(!(reader instanceof DirectiveStringReader<?> directiveStringReader)) {
            return c;
        }
        if(directiveStringReader.getScopeStack().element().getClosure().endsClosure(directiveStringReader, true)) {
            return ARGUMENT_SEPARATOR_CHAR;
        }
        if(!VanillaLanguage.Companion.isReaderEasyNextLine(reader)) {
            return c;
        }
        if(reader.canRead() && reader.peek() == '\n') {
            return ARGUMENT_SEPARATOR_CHAR;
        }
        return c;
    }

    @WrapOperation(
            method = "parseNodes",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;canRead(I)Z"
            )
    )
    private boolean command_crafter$endOnClosureExitOrNewLine(StringReader reader, int amount, Operation<Boolean> op) {
        if(!op.call(reader, amount)) {
            return false;
        }
        if(reader instanceof DirectiveStringReader<?> directiveStringReader) {
            if(directiveStringReader.getScopeStack().element().getClosure().endsClosure(directiveStringReader, true)) {
                return false;
            }
            if(VanillaLanguage.Companion.isReaderEasyNextLine(reader) && reader.canRead() && reader.peek() == '\n') {
                if(!VanillaLanguage.Companion.skipImprovedCommandGap(directiveStringReader)) {
                    return false;
                }
                reader.setCursor(reader.getCursor() - 1);
            }
        }
        return true;
    }

    @Inject(
            method = "parseNodes -> Ljava/util/Comparator;compare(Lcom/mojang/brigadier/ParseResults;Lcom/mojang/brigadier/ParseResults;)I", // The lambda passed to sort
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/ParseResults;getExceptions()Ljava/util/Map;",
                    ordinal = 0
            ),
            cancellable = true
    )
    private static void command_crafter$useFurtherParsedResults(ParseResults<?> a, ParseResults<?> b, CallbackInfoReturnable<Integer> cir) {
        var readerA = a.getReader();
        var readerB = b.getReader();
        if(!VanillaLanguage.Companion.isReaderVanilla(readerA) || !VanillaLanguage.Companion.isReaderVanilla(readerB))
            return;
        var cursorA = ((DirectiveStringReader<?>) readerA).getAbsoluteCursor();
        var cursorB = ((DirectiveStringReader<?>) readerB).getAbsoluteCursor();

        var lengthCompare = Integer.compare(cursorB, cursorA);
        if(lengthCompare != 0) {
            cir.setReturnValue(lengthCompare);
        }
    }

    @ModifyVariable(
            method = "parseNodes",
            at = @At("STORE"),
            name = "reader"
    )
    private <S> StringReader command_crafter$copyDirectiveStringReader(StringReader defaultCopied, CommandNode<S> node, StringReader original) {
        return original instanceof DirectiveStringReader<?> directiveOriginal
                ? directiveOriginal.copy()
                : defaultCopied;
    }

    @ModifyExpressionValue(
            method = "parse(Lcom/mojang/brigadier/StringReader;Ljava/lang/Object;)Lcom/mojang/brigadier/ParseResults;",
            at = @At(
                    value = "NEW",
                    target = "com/mojang/brigadier/context/CommandContextBuilder"
            )
    )
    private CommandContextBuilder<?> command_crafter$setContextBuilderReader(CommandContextBuilder<?> builder, @Local(argsOnly = true) StringReader reader) {
        if(reader instanceof DirectiveStringReader<?> directiveStringReader) {
            ((DirectiveStringReaderConsumer)builder).command_crafter$setStringReader(directiveStringReader);
        }
        return builder;
    }

    @ModifyExpressionValue(
            method = "parseNodes",
            at = @At(
                    value = "NEW",
                    target = "com/mojang/brigadier/context/CommandContextBuilder"
            )
    )
    private CommandContextBuilder<?> command_crafter$setRedirectContextBuilderReader(CommandContextBuilder<?> builder, @Local(name = "reader") StringReader reader) {
        if(reader instanceof DirectiveStringReader<?> directiveStringReader) {
            ((DirectiveStringReaderConsumer)builder).command_crafter$setStringReader(directiveStringReader);
        }
        return builder;
    }

    @ModifyArg(
            method = "parseNodes",
            at = @At(
                    value = "INVOKE",
                    target= "Lcom/mojang/brigadier/tree/CommandNode;parse(Lcom/mojang/brigadier/StringReader;Lcom/mojang/brigadier/context/CommandContextBuilder;)V"
            )
    )
    private CommandContextBuilder<?> command_crafter$setChildContextBuilderReader(StringReader reader, CommandContextBuilder<?> builder) {
        if(reader instanceof DirectiveStringReader<?> directiveStringReader) {
            ((DirectiveStringReaderConsumer)builder).command_crafter$setStringReader(directiveStringReader);
        }
        return builder;
    }
}
