package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.packrat.ParsingState;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.UnparsedArgumentContainer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(ParsingState.PackratCache.class)
public class PackratCacheMixin implements UnparsedArgumentContainer {

    @Nullable
    private List<Either<String, RawResource>> command_crafter$unparsedArgument;

    @Override
    public List<Either<String, RawResource>> command_crafter$getUnparsedArgument() {
        return command_crafter$unparsedArgument;
    }

    @Override
    public void command_crafter$setUnparsedArgument(@Nullable List<Either<String, RawResource>> arg) {
        command_crafter$unparsedArgument = arg;
    }
}

