package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.RawResourceRegistryEntryList;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(BlockPredicateArgument.class)
public class BlockPredicateArgumentMixin implements StringifiableArgumentType {

    @SuppressWarnings("RedundantThrows")
    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        final var predicate = context.getArgument(name, BlockPredicateArgument.Result.class);
        List<Either<String, RawResource>> result = new ArrayList<>();
        if(predicate instanceof BlockPredicateAccessor block) {
            result.add(Either.left(BuiltInRegistries.BLOCK.getKey(block.getState().getBlock()).toString()));
            if(!block.getProperties().isEmpty()) {
                final var properties = block.getProperties().stream()
                        .map(property -> command_crafter$stringifyProperty(property, block.getState()))
                        .collect(Collectors.joining(","));
                result.add(Either.left('[' + properties + ']'));
            }
            if(block.getNbt() != null)
                result.add(Either.left(block.getNbt().toString()));
        } else if(predicate instanceof TagPredicateAccessor tag) {
            result.add(Either.left("#"));
            if(tag.getTag() instanceof RawResourceRegistryEntryList<Block> rawResource) {
                result.add(Either.right(rawResource.getResource()));
            } else {
                result.add(Either.left(tag.getTag().unwrapKey().orElseThrow().toString()));
            }
            if(!tag.getVagueProperties().isEmpty()) {
                final var properties = tag.getVagueProperties().entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(","));
                result.add(Either.left('[' + properties + ']'));
            }
            if(tag.getNbt() != null)
                result.add(Either.left(tag.getNbt().toString()));
        } else {
            throw new IllegalArgumentException("Unknown block predicate type: " + predicate);
        }
        return result;
    }

    private <T extends Comparable<T>> String command_crafter$stringifyProperty(Property<T> property, BlockState state) {
        return property.getName() + "=" + property.getName(state.getValue(property));
    }

    @Mixin(targets = "net.minecraft.commands.arguments.blocks.BlockPredicateArgument$BlockPredicate")
    public interface BlockPredicateAccessor {
        @Accessor
        BlockState getState();
        @Accessor
        Set<Property<?>> getProperties();
        @Accessor
        CompoundTag getNbt();
    }

    @Mixin(targets = "net.minecraft.commands.arguments.blocks.BlockPredicateArgument$TagPredicate")
    public interface TagPredicateAccessor {
        @Accessor
        HolderSet<Block> getTag();
        @Accessor
        Map<String, String> getVagueProperties();
        @Accessor
        CompoundTag getNbt();
    }
}
