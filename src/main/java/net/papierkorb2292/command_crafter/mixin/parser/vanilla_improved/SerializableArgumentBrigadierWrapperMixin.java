package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.SerializableArgument;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;


@Mixin(SerializableArgument.BrigadierWrapper.class)
public interface SerializableArgumentBrigadierWrapperMixin<T extends SerializableArgument<?>> extends StringifiableArgumentType {
    @Shadow
    SerializableArgument<T> argument();

    @Nullable
    @Override
    default List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        if(argument() instanceof StringifiableArgumentType stringifiable) {
            return stringifiable.command_crafter$stringifyArgument(context, name, reader);
        }
        return null;
    }
}
