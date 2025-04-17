package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.MessageArgumentType;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MessageArgumentType.MessageFormat.class)
public class MessageArgumentTypeMessageFormatMixin {
    @WrapMethod(
            method = "parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/command/argument/MessageArgumentType$MessageFormat;"
    )
    private static MessageArgumentType.MessageFormat command_crafter$fixMultiline(StringReader reader, boolean canUseSelectors, Operation<MessageArgumentType.MessageFormat> op) {
        if(!(reader instanceof DirectiveStringReader<?> directiveReader) || directiveReader.getOnlyReadEscapedMultiline()) {
            return op.call(reader, canUseSelectors);
        }

        directiveReader.setOnlyReadEscapedMultiline(true);
        final var prevCursorMapper = directiveReader.getFileMappingInfo().getCursorMapper().copy();
        try {
            directiveReader.canRead();
            return op.call(reader, canUseSelectors);
        } catch(Exception e) {
            // Restore mappings
            directiveReader.getFileMappingInfo().getCursorMapper().copyFrom(prevCursorMapper);
            throw e;
        } finally {
            directiveReader.setOnlyReadEscapedMultiline(false);
        }
    }

}
