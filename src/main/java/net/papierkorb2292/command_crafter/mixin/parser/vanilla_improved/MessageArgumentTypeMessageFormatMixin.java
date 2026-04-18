package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.MessageArgument;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MessageArgument.Message.class)
public class MessageArgumentTypeMessageFormatMixin {
    @WrapMethod(
            method = "parseText(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/commands/arguments/MessageArgument$Message;"
    )
    private static MessageArgument.Message command_crafter$fixMultiline(StringReader reader, boolean canUseSelectors, Operation<MessageArgument.Message> op) {
        if(!(reader instanceof DirectiveStringReader<?> directiveReader) || directiveReader.getOnlyReadEscapedMultiline()) {
            return op.call(reader, canUseSelectors);
        }

        directiveReader.convertInputToEscapedMultiline();
        try {
            directiveReader.canRead();
            return op.call(reader, canUseSelectors);
        } finally {
            directiveReader.disableEscapedMultiline();
            // Don't restore mappings on error, because the input should probably still be interpreted as a message argument
        }
    }

}
