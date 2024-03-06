package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import net.minecraft.command.argument.MessageArgumentType;
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapper;
import net.papierkorb2292.command_crafter.parser.helper.ProcessedInputCursorMapperContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MessageArgumentType.MessageFormat.class)
public class MessageFormatMixin implements ProcessedInputCursorMapperContainer {
    private ProcessedInputCursorMapper command_crafter$messageCursorMapper = null;

    @Nullable
    @Override
    public ProcessedInputCursorMapper command_crafter$getProcessedInputCursorMapper() {
        return command_crafter$messageCursorMapper;
    }

    @Override
    public void command_crafter$setProcessedInputCursorMapper(@NotNull ProcessedInputCursorMapper mapper) {
        command_crafter$messageCursorMapper = mapper;
    }
}
