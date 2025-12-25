package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.papierkorb2292.command_crafter.editor.processing.ArgumentTypeAdditionalDataSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ArgumentTypeInfos.class)
public class ArgumentTypeInfosMixin {

    @ModifyArg(
            method = "bootstrap(Lnet/minecraft/core/Registry;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/synchronization/ArgumentTypeInfos;register(Lnet/minecraft/core/Registry;Ljava/lang/String;Ljava/lang/Class;Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=resource_location"
                    )
            )
    )
    private static <A extends ArgumentType<A>> ArgumentTypeInfo<A, ArgumentTypeInfo.Template<A>> command_crafter$wrapIdSerializer(ArgumentTypeInfo<A, ArgumentTypeInfo.Template<A>> serializer) {
        return new ArgumentTypeAdditionalDataSerializer<>(serializer);
    }

    @ModifyArg(
            method = "bootstrap(Lnet/minecraft/core/Registry;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/synchronization/ArgumentTypeInfos;register(Lnet/minecraft/core/Registry;Ljava/lang/String;Ljava/lang/Class;Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=resource_key"
                    )
            )
    )
    private static <A extends ArgumentType<A>> ArgumentTypeInfo<A, ArgumentTypeInfo.Template<A>> command_crafter$wrapRegistryKeySerializer(ArgumentTypeInfo<A, ArgumentTypeInfo.Template<A>> serializer) {
        return new ArgumentTypeAdditionalDataSerializer<>(serializer);
    }

    @ModifyArg(
            method = "bootstrap(Lnet/minecraft/core/Registry;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/synchronization/ArgumentTypeInfos;register(Lnet/minecraft/core/Registry;Ljava/lang/String;Ljava/lang/Class;Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;)Lnet/minecraft/commands/synchronization/ArgumentTypeInfo;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "stringValue=nbt_compound_tag"
                    )
            )
    )
    private static <A extends ArgumentType<A>> ArgumentTypeInfo<A, ArgumentTypeInfo.Template<A>> command_crafter$wrapNbtCompoundSerializer(ArgumentTypeInfo<A, ArgumentTypeInfo.Template<A>> serializer) {
        return new ArgumentTypeAdditionalDataSerializer<>(serializer);
    }
}
