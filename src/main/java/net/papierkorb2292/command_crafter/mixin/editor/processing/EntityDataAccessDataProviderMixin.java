package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.common.collect.ImmutableSet;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.SerializableArgument;
import net.minecraft.server.commands.ArgProvider;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.papierkorb2292.command_crafter.editor.processing.AdditionalArgumentTypeData;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingCommandTree;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.ArgProviderDataObjectSourceApplier;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(EntityDataAccessor.class)
public class EntityDataAccessDataProviderMixin {

    @Mutable
    @Shadow
    @Final
    public static ArgProvider.Factory<DataAccessor> PROVIDER;
    private static final Set<String> command_crafter$mutatingCommands = ImmutableSet.of("merge", "modify", "remove", "result", "success");

    @Inject(
            method = "<clinit>",
            at = @At("TAIL")
    )
    private static void command_crafter$setDataObjectSource(CallbackInfo ci) {
        PROVIDER = ArgProviderDataObjectSourceApplier.Companion.wrapFactory(PROVIDER, (arg, parent) -> {
            if(!AnalyzingCommandTree.Companion.getBUILDER().isBound())
                return;
            var builder = AnalyzingCommandTree.Companion.getBUILDER().get();
            var requiredArgument = (RequiredArgumentBuilder<?, ?>) arg;
            var argumentName = requiredArgument.getName();
            var mutating = parent instanceof LiteralArgumentBuilder<?> literal && command_crafter$mutatingCommands.contains(literal.getLiteral());
            if(mutating) {
                // Player NBT can't be edited, so we can be sure that no player should be selected by this selector argument
                builder.addData(requiredArgument.getType(), AdditionalArgumentTypeData.INSTANCE.getNonPlayerSelector(), true);
            }
            for(final var child : arg.getArguments()) {
                if(!(child instanceof ArgumentCommandNode<?,?> argument))
                    continue;
                var serializable = SerializableArgument.unwrapOrThrow(argument.getType());
                if(serializable instanceof CompoundTagArgument) {
                    builder.addData(argument.getType(), AdditionalArgumentTypeData.INSTANCE.getDataObjectSource(), new DataObjectDecoding.DataObjectSource(DataObjectDecoding.DataObjectSourceKind.ENTITY_CHANGE, argumentName));
                } else if(serializable instanceof NbtPathArgument) {
                    final var kind = parent instanceof LiteralArgumentBuilder<?> literal && command_crafter$mutatingCommands.contains(literal.getLiteral()) ? DataObjectDecoding.DataObjectSourceKind.MUTATING_ENTITY_LOOKUP : DataObjectDecoding.DataObjectSourceKind.ENTITY_LOOKUP;
                    builder.addData(argument.getType(), AdditionalArgumentTypeData.INSTANCE.getDataObjectSource(), new DataObjectDecoding.DataObjectSource(kind, argumentName));
                }
            }
        });
    }
}
