package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandBox;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.ValidationWrapper;
import net.minecraft.core.Holder;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
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
public class ResourceOrIdArgumentMixin<Raw, Value> implements StringifiableArgumentType {
    @Shadow @Final private Codec<Raw> codec;

    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        var argument = context.getArgument(name, Object.class);
        Holder<?> holder;
        if(argument instanceof Holder<?> holder2) {
            holder = holder2;
        } else if(argument instanceof ValidationWrapper<?> wrapper) {
            holder = ((ValidationWrapperAccessor<?>)wrapper).getResult();
        } else {
            throw new IllegalStateException("ResourceOrIdArgument value was expected to be either a holder or validation wrapper");
        }
        RegistryOps<Tag> registryOps = CommandBox.grabParseContext().holderGetters().createSerializationContext(NbtOps.INSTANCE);
        //noinspection unchecked
        return Collections.singletonList(Either.left(holder.unwrap().map(
                key -> key.identifier().toShortString(),
                value -> codec.encode((Raw)value, registryOps, registryOps.empty()).getOrThrow().toString()
        )));
    }
}
