package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.CommandBox;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.SlotSourceArgument;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.SlotRanges;
import net.minecraft.world.item.slot.SlotSource;
import net.minecraft.world.item.slot.SlotSources;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collections;
import java.util.List;

@Mixin(SlotSourceArgument.class)
public class SlotSourceArgumentMixin implements StringifiableArgumentType {

    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        var argument = context.getArgument(name, SlotSourceArgument.SlotResult.class);
        var literal = SlotRanges.tryRead(reader);
        if(literal != null) {
            return null; // Use default behavior of copying the input string, because the syntax for literal results is unchanged
        }
        // Result must be from ResourceOrIdArgument
        @SuppressWarnings("unchecked")
        var holder = ((ValidationWrapperAccessor<SlotSource>)argument.value()).getResult();
        RegistryOps<Tag> registryOps = CommandBox.grabParseContext().holderGetters().createSerializationContext(NbtOps.INSTANCE);
        return Collections.singletonList(Either.left(holder.unwrap().map(
                key -> key.identifier().toShortString(),
                value -> SlotSources.DIRECT_CODEC.encode(value, registryOps, registryOps.empty()).getOrThrow().toString()
        )));
    }
}
