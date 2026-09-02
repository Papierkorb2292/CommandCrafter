package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.server.commands.ArgProvider;
import net.minecraft.server.commands.data.BlockDataAccessor;
import net.minecraft.server.commands.data.DataAccessor;
import net.papierkorb2292.command_crafter.editor.processing.helper.DataObjectSourceContainer;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.ArgProviderDataObjectSourceApplier;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockDataAccessor.class)
public class BlockDataAccessorDataProviderMixin {
    @Mutable
    @Shadow
    @Final
    public static ArgProvider.Factory<DataAccessor> PROVIDER;

    @Inject(
            method = "<clinit>",
            at = @At("TAIL")
    )
    private static void command_crafter$setDataObjectSource(CallbackInfo ci) {
        PROVIDER = ArgProviderDataObjectSourceApplier.Companion.wrapFactory(PROVIDER, (arg, parent) -> {
            var argumentName = ((RequiredArgumentBuilder<?, ?>) arg).getName();
            for(final var child : arg.getArguments()) {
                if(!(child instanceof ArgumentCommandNode<?,?> argument))
                    continue;
                if(argument.getType() instanceof CompoundTagArgument compoundArg) {
                    ((DataObjectSourceContainer)compoundArg).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.BLOCK_ENTITY_CHANGE, argumentName));
                } else if(argument.getType() instanceof NbtPathArgument nbtPathArg) {
                    ((DataObjectSourceContainer)nbtPathArg).command_crafter$setDataObjectSource(new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.BLOCK_ENTITY_LOOKUP, argumentName));
                }
            }
        });
    }
}
