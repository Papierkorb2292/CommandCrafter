package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.ArrayList;
import java.util.List;

@Mixin(ParticleArgument.class)
public class ParticleArgumentMixin implements StringifiableArgumentType {
    @Override
    public @Nullable List<@NotNull Either<@NotNull String, @NotNull RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<@NotNull CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<@NotNull RawZipResourceCreator> reader) throws CommandSyntaxException {
        final var result = new ArrayList<Either<String, RawResource>>();
        final var options = context.getArgument(name, ParticleOptions.class);
        final var type = options.getType();
        result.add(Either.left(BuiltInRegistries.PARTICLE_TYPE.getKey(type).toString()));
        final var nbt = command_crafter$buildParticleNbt(type, options);
        if(!nbt.isEmpty()) {
            result.add(Either.left(nbt.toString()));
        }
        return result;
    }

    private <T extends ParticleOptions> CompoundTag command_crafter$buildParticleNbt(ParticleType<T> type, ParticleOptions options) {
        return (CompoundTag)type.codec().codec().encode((T)options, NbtOps.INSTANCE, NbtOps.INSTANCE.emptyMap()).getOrThrow();
    }
}
