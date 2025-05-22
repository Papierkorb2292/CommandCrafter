package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.command.argument.serialize.ArgumentSerializer;
import net.papierkorb2292.command_crafter.editor.processing.ArgumentTypeAdditionalDataSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ArgumentTypes.class)
public class ArgumentTypesMixin {

    @ModifyArg(
            method = "register(Lnet/minecraft/registry/Registry;)Lnet/minecraft/command/argument/serialize/ArgumentSerializer;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/ArgumentTypes;register(Lnet/minecraft/registry/Registry;Ljava/lang/String;Ljava/lang/Class;Lnet/minecraft/command/argument/serialize/ArgumentSerializer;)Lnet/minecraft/command/argument/serialize/ArgumentSerializer;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=resource_location"
                    )
            )
    )
    private static <A extends ArgumentType<A>> ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> command_crafter$wrapIdSerializer(ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> serializer) {
        return new ArgumentTypeAdditionalDataSerializer<>(serializer);
    }

    @ModifyArg(
            method = "register(Lnet/minecraft/registry/Registry;)Lnet/minecraft/command/argument/serialize/ArgumentSerializer;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/ArgumentTypes;register(Lnet/minecraft/registry/Registry;Ljava/lang/String;Ljava/lang/Class;Lnet/minecraft/command/argument/serialize/ArgumentSerializer;)Lnet/minecraft/command/argument/serialize/ArgumentSerializer;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=resource_key"
                    )
            )
    )
    private static <A extends ArgumentType<A>> ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> command_crafter$wrapRegistryKeySerializer(ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> serializer) {
        return new ArgumentTypeAdditionalDataSerializer<>(serializer);
    }

    @ModifyArg(
            method = "register(Lnet/minecraft/registry/Registry;)Lnet/minecraft/command/argument/serialize/ArgumentSerializer;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/ArgumentTypes;register(Lnet/minecraft/registry/Registry;Ljava/lang/String;Ljava/lang/Class;Lnet/minecraft/command/argument/serialize/ArgumentSerializer;)Lnet/minecraft/command/argument/serialize/ArgumentSerializer;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=nbt_compound_tag"
                    )
            )
    )
    private static <A extends ArgumentType<A>> ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> command_crafter$wrapNbtCompoundSerializer(ArgumentSerializer<A, ArgumentSerializer.ArgumentTypeProperties<A>> serializer) {
        return new ArgumentTypeAdditionalDataSerializer<>(serializer);
    }
}
