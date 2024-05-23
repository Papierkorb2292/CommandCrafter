package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.argument.MessageArgumentType;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MessageArgumentType.class)
public class MessageArgumentTypeMixin {
    @WrapOperation(
            method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/command/argument/MessageArgumentType$MessageFormat;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/MessageArgumentType$MessageFormat;parse(Lcom/mojang/brigadier/StringReader;Z)Lnet/minecraft/command/argument/MessageArgumentType$MessageFormat;"
            )
    )
    private MessageArgumentType.MessageFormat command_crafter$fixMultiline(StringReader reader, boolean canUseSelectors, Operation<MessageArgumentType.MessageFormat> op) {
        if(!(reader instanceof DirectiveStringReader<?> directiveReader) || directiveReader.getOnlyReadEscapedMultiline()) {
            return op.call(reader, canUseSelectors);
        }

        directiveReader.setOnlyReadEscapedMultiline(true);
        try {
            directiveReader.canRead();
            return op.call(reader, canUseSelectors);
        } finally {
            directiveReader.setOnlyReadEscapedMultiline(false);
        }
    }

}
