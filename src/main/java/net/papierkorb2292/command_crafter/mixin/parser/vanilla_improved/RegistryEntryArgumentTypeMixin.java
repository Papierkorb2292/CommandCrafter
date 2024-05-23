package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.ServerCommandSource;
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

@Mixin(RegistryEntryArgumentType.class)
public class RegistryEntryArgumentTypeMixin<T> implements StringifiableArgumentType {
    @Shadow @Final private Codec<RegistryEntry<T>> entryCodec;

    @Shadow @Final private RegistryWrapper.WrapperLookup registryLookup;

    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<ServerCommandSource> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        //noinspection unchecked
        var argument = (RegistryEntry<T>)context.getArgument(name, RegistryEntry.class);
        RegistryOps<NbtElement> registryOps = registryLookup.getOps(NbtOps.INSTANCE);
        return Collections.singletonList(Either.left(entryCodec.encode(argument, registryOps, registryOps.empty()).getOrThrow().asString()));
    }
}
