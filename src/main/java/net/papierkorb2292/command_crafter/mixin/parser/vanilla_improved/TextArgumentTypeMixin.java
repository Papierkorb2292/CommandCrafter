package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.datafixers.util.Either;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.RawZipResourceCreator;
import net.papierkorb2292.command_crafter.parser.helper.RawResource;
import net.papierkorb2292.command_crafter.parser.helper.UnparsableArgumentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(TextArgumentType.class)
public abstract class TextArgumentTypeMixin implements UnparsableArgumentType {


    @Shadow
    public static Text getTextArgument(CommandContext<ServerCommandSource> context, String name) {
        throw new AssertionError();
    }

    @Shadow @Final private RegistryWrapper.WrapperLookup registryLookup;

    @Nullable
    @Override
    public List<Either<String, RawResource>> command_crafter$unparseArgument(@NotNull CommandContext<ServerCommandSource> context, @NotNull String name, @NotNull DirectiveStringReader<RawZipResourceCreator> reader) {
        return List.of(Either.left(Text.Serialization.toJsonString(getTextArgument(context, name), registryLookup)));
    }
}

