package net.papierkorb2292.command_crafter.mixin.parser;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.helper.ServerSourceAware;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("unused")
@Mixin(CommandDispatcher.class)
public class CommandDispatcherMixin {
    @Shadow(remap = false) @Final public static char ARGUMENT_SEPARATOR_CHAR;

    @ModifyExpressionValue(
            method = "parseNodes",
            at = @At(
                value = "INVOKE",
                target = "Lcom/mojang/brigadier/StringReader;peek()C"
            ),
            remap = false
    )
    private char command_crafter$allowMultilineLiteralSeparator(char c, @Local(ordinal = 1) StringReader reader) {
        if(!(reader instanceof DirectiveStringReader<?> directiveStringReader)) {
            return c;
        }
        if(directiveStringReader.getScopeStack().element().getClosure().endsClosure(reader)) {
            return ARGUMENT_SEPARATOR_CHAR;
        }
        if(!VanillaLanguage.Companion.isReaderImproved(reader)) {
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
            ),
            remap = false
    )
    private boolean command_crafter$endOnClosureExitOrNewLine(StringReader reader, int amount, Operation<Boolean> op) {
        if(!op.call(reader, amount)) {
            return false;
        }
        if(reader instanceof DirectiveStringReader<?> directiveStringReader) {
            if(directiveStringReader.getScopeStack().element().getClosure().endsClosure(reader)) {
                return false;
            }
            if(VanillaLanguage.Companion.isReaderImproved(reader) && reader.canRead() && reader.peek() == '\n') {
                var cursor = reader.getCursor();
                if(!VanillaLanguage.Companion.skipImprovedCommandGap(directiveStringReader)) {
                    reader.setCursor(cursor);
                    return false;
                }
                reader.setCursor(reader.getCursor() - 1);
            }
        }
        return true;
    }

    @Inject(
            method = "lambda$parseNodes$1(Lcom/mojang/brigadier/ParseResults;Lcom/mojang/brigadier/ParseResults;)I",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/ParseResults;getExceptions()Ljava/util/Map;"
            ),
            cancellable = true,
            remap = false
    )
    private static void command_crafter$useFurtherParsedResults(ParseResults<?> a, ParseResults<?> b, CallbackInfoReturnable<Integer> cir) {
        var reader = a.getReader();
        var cursorA = a.getReader().getCursor();
        if(reader instanceof DirectiveStringReader<?> directiveStringReader)
            cursorA += directiveStringReader.getReadCharacters();

        reader = b.getReader();
        var cursorB = b.getReader().getCursor();
        if(reader instanceof DirectiveStringReader<?> directiveStringReader)
            cursorB += directiveStringReader.getReadCharacters();
        var lengthCompare = Integer.compare(cursorB, cursorA);
        if(lengthCompare != 0) {
            cir.setReturnValue(lengthCompare);
        }
    }

    @ModifyVariable(
            method = "parseNodes",
            at = @At("STORE"),
            remap = false,
            ordinal = 1
    )
    private <S> StringReader command_crafter$copyDirectiveStringReader(StringReader defaultCopied, CommandNode<S> node, StringReader original) {
        return original instanceof DirectiveStringReader<?> directiveOriginal
                ? directiveOriginal.copy()
                : defaultCopied;
    }

    @ModifyReceiver(
            method = "parseNodes",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/tree/CommandNode;parse(Lcom/mojang/brigadier/StringReader;Lcom/mojang/brigadier/context/CommandContextBuilder;)V"
            ),
            remap = false
    )
    private CommandNode<Object> command_crafter$makeChildMultilineAware(CommandNode<Object> node, StringReader reader, CommandContextBuilder<Object> context) {
        if(node instanceof ServerSourceAware serverSourceAware && context.getSource() instanceof ServerCommandSource source) {
            serverSourceAware.command_crafter$setServerCommandSource(source);
        }
        return node;
    }
}
