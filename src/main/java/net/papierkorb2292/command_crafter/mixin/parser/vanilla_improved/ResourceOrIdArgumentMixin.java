package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Holder;
import net.minecraft.commands.CommandSourceStack;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Collections;
import java.util.List;

@Mixin(ResourceOrIdArgument.class)
public class ResourceOrIdArgumentMixin<T> implements StringifiableArgumentType {
    @Shadow @Final private Codec<T> codec;

    @Shadow @Final private HolderLookup.Provider registryLookup;

    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        //noinspection unchecked
        var argument = (Holder<T>)context.getArgument(name, Holder.class);
        RegistryOps<Tag> registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        return Collections.singletonList(Either.left(argument.unwrap().map(
                key -> key.identifier().toShortString(),
                value -> codec.encode(value, registryOps, registryOps.empty()).getOrThrow().toString()
        )));
    }
}
