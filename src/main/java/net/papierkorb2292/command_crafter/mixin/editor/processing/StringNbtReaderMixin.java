package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import net.minecraft.nbt.*;
import net.minecraft.util.packrat.PackratParser;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(StringNbtReader.class)
public abstract class StringNbtReaderMixin<T> implements StringRangeTreeCreator<NbtElement>, AllowMalformedContainer {
    private @Nullable StringRangeTree.Builder<NbtElement> command_crafter$stringRangeTreeBuilder;
    private boolean command_crafter$allowMalformed = false;

    @Override
    public void command_crafter$setAllowMalformed(boolean allowMalformed) {
        command_crafter$allowMalformed = allowMalformed;
    }

    @Override
    public void command_crafter$setStringRangeTreeBuilder(@NotNull StringRangeTree.Builder<NbtElement> builder) {
        command_crafter$stringRangeTreeBuilder = builder;
    }

    @WrapOperation(
            method = { "read(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;", "readAsArgument" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/packrat/PackratParser;parse(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private T command_crafter$setAdditionalPackratParserArgs(PackratParser<T> instance, StringReader reader, Operation<T> op) {
        var restoreArgsCallback = PackratParserAdditionalArgs.INSTANCE.temporarilyClearArgs();

        PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().set(command_crafter$allowMalformed);
        StringRangeTree.PartialBuilder<NbtElement> partialStringRangeTreeBuilder = null;
        if(command_crafter$stringRangeTreeBuilder != null) {
            partialStringRangeTreeBuilder = new StringRangeTree.PartialBuilder<>();
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().set(new PackratParserAdditionalArgs.StringRangeTreeBranchingArgument<>(partialStringRangeTreeBuilder));
        }

        try {
            return op.call(instance, reader);
        } finally {
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().remove();
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().remove();
            if(partialStringRangeTreeBuilder != null) {
                partialStringRangeTreeBuilder.addToBasicBuilder(command_crafter$stringRangeTreeBuilder);
            }
            restoreArgsCallback.invoke();
        }
    }
}