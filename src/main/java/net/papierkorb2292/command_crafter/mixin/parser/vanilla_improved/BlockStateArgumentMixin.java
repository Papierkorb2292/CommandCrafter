package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mixin(BlockStateArgument.class)
public class BlockStateArgumentMixin implements StringifiableArgumentType {
    @Override
    public @Nullable List<@NotNull Either<@NotNull String, @NotNull RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<@NotNull CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<@NotNull RawZipResourceCreator> reader) throws CommandSyntaxException {
        final var result = new ArrayList<Either<String, RawResource>>();
        final var blockInput = context.getArgument(name, BlockInput.class);
        result.add(Either.left(BuiltInRegistries.BLOCK.getKey(blockInput.getState().getBlock()).toString()));
        if(!blockInput.getDefinedProperties().isEmpty()) {
            final var properties = blockInput.getDefinedProperties().stream()
                    .map(property -> command_crafter$stringifyProperty(property, blockInput.getState()))
                    .collect(Collectors.joining(","));
            result.add(Either.left('[' + properties + ']'));
        }
        final var nbt = ((BlockInputAccessor)blockInput).getTag();
        if(nbt != null)
            result.add(Either.left(nbt.toString()));
        return result;
    }

    private <T extends Comparable<T>> String command_crafter$stringifyProperty(Property<T> property, BlockState state) {
        return property.getName() + "=" + property.getName(state.getValue(property));
    }
}
