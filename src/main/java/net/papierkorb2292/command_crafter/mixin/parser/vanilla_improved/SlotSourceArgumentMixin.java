package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.SlotSourceArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.slot.SlotSources;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.StringifiableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

@Mixin(SlotSourceArgument.class)
public class SlotSourceArgumentMixin implements StringifiableArgumentType {

    private HolderLookup.Provider command_crafter$registryLookup;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$storeRegistryLookup(CommandBuildContext context, CallbackInfo ci) {
        command_crafter$registryLookup = context;
    }

    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$stringifyArgument(@NotNull CommandContext<CommandSourceStack> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) throws CommandSyntaxException {
        var argument = context.getArgument(name, SlotSourceArgument.Result.class);
        if(argument instanceof SlotSourceArgument.LiteralResult) {
            return null; // Use default behavior of copying the input string, because the syntax for literal results is unchanged
        }
        // Result must be from ResourceOrIdArgument
        var holder = ((SlotSourceArgument.HolderResult)argument).holder();
        RegistryOps<Tag> registryOps = command_crafter$registryLookup.createSerializationContext(NbtOps.INSTANCE);
        return Collections.singletonList(Either.left(holder.unwrap().map(
                key -> key.identifier().toShortString(),
                value -> SlotSources.DIRECT_CODEC.encode(value, registryOps, registryOps.empty()).getOrThrow().toString()
        )));
    }
}
