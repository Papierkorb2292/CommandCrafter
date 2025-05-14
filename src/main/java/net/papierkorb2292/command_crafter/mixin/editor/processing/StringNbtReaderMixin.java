package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.*;
import net.minecraft.util.packrat.PackratParser;
import net.papierkorb2292.command_crafter.editor.processing.PreLaunchDecoderOutputTracker;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.PackratParserAdditionalArgs;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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

    private static ThreadLocal<Object> command_crafter$parseStringErrorInput = new ThreadLocal<>();

    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/codecs/PrimitiveCodec;comapFlatMap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"
            ),
            remap = false
    )
    private static Codec<NbtCompound> command_crafter$storeStringForStringParseErrorCallback(Codec<NbtCompound> codec) {
        return new Codec<>() {
            @Override
            public <U> DataResult<Pair<NbtCompound, U>> decode(DynamicOps<U> ops, U input) {
                command_crafter$parseStringErrorInput.set(input);
                var result = codec.decode(ops, input);
                command_crafter$parseStringErrorInput.remove();
                return result;
            }

            @Override
            public <U> DataResult<U> encode(NbtCompound input, DynamicOps<U> ops, U prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }

    @ModifyExpressionValue(
            method = "method_53502",
            at = @At(
                    value = "INVOKE:LAST",
                    target = "Lcom/mojang/serialization/DataResult;error(Ljava/util/function/Supplier;)Lcom/mojang/serialization/DataResult;"
            ),
            remap = false
    )
    private static DataResult<?> command_crafter$invokeStringParseErrorCallback(DataResult<?> result, @Local CommandSyntaxException exception) {
        PreLaunchDecoderOutputTracker.INSTANCE.onStringParseError((DataResult.Error<?>)result, command_crafter$parseStringErrorInput.get(), exception.getCursor());
        return result;
    }
}