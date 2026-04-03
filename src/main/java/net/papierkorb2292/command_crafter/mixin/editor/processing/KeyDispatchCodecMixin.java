package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.codecs.KeyDispatchCodec;
import net.papierkorb2292.command_crafter.CommandCrafter;
import net.papierkorb2292.command_crafter.editor.processing.codecmod.DecoderPossibleValueTracker;
import net.papierkorb2292.command_crafter.editor.processing.codecmod.ExtraDecoderBehavior;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

@Mixin(KeyDispatchCodec.class)
public class KeyDispatchCodecMixin<K, V> {

    @Final
    @Shadow
    private MapCodec<K> keyCodec;

    @WrapOperation(
            method = "decode",
            at = @At(
                    value = "INVOKE",
                    target = "com/mojang/serialization/DataResult.flatMap(Ljava/util/function/Function;)Lcom/mojang/serialization/DataResult;"
            )
    )
    private <T> DataResult<?> command_crafter$analyzePossibleBranches(DataResult<K> typeResult, Function<K, DataResult<?>> valueDecoder, Operation<DataResult<?>> op, DynamicOps<T> ops, MapLike<T> input) {
        final var result = op.call(typeResult, valueDecoder);
        if(!typeResult.isError())
            return result;
        final var extraBehavior = ExtraDecoderBehavior.Companion.getCurrentBehavior(ops);
        if(extraBehavior == null || !extraBehavior.getBranchBehavior().isAllPossibleEncoded())
            return result;

        final var possibleKeyTracker = new DecoderPossibleValueTracker<T>();
        final var dispatchValue = new MutableObject<Pair<T, Boolean>>();
        final var lenientAccessTrackingMap = new MapLike<T>() {
            private void onAccessedKey(T key) {
                if(dispatchValue.get() == null)
                    dispatchValue.setValue(Pair.of(key, false));
                else
                    dispatchValue.setValue(Pair.of(null, true));
            }

            @Override
            public T get(T key) {
                onAccessedKey(key);
                final var value =  input.get(key);
                return value != null ? value : ops.empty();
            }

            @Override
            public T get(String key) {
                onAccessedKey(ops.createString(key));
                final var value =  input.get(key);
                return value != null ? value : ops.empty();
            }

            @Override
            public Stream<Pair<T, T>> entries() {
                return input.entries();
            }

            @Override
            public String toString() {
                return input.toString();
            }
        };
        ExtraDecoderBehavior.Companion.decodeWithBehavior(keyCodec, ops, lenientAccessTrackingMap, possibleKeyTracker);

        // Analyzing assumes that exactly one key was exacted by the key codec
        if(dispatchValue.get() == null || dispatchValue.get().getSecond()) {
            CommandCrafter.INSTANCE.getLOGGER().debug("Dispatcher key codec did not access exactly one key: {}", keyCodec.toString());
            command_crafter$onMissingPossibleKey(input, extraBehavior);
            return result;
        }

        final var key = dispatchValue.get().getFirst();
        final var possibleValues = possibleKeyTracker.getPossibleValues().get(lenientAccessTrackingMap.get(key));
        if(possibleValues == null) {
            CommandCrafter.INSTANCE.getLOGGER().debug("Dispatcher key codec did not provide possible values: {}", keyCodec.toString());
            command_crafter$onMissingPossibleKey(input, extraBehavior);
            return result;
        }

        for(final var possibleValue : possibleValues) {
            keyCodec.decode(ops, MapLike.forMap(Map.of(key, possibleValue), ops)).result().ifPresent(valueDecoder::apply);
        }

        return result;
    }

    private <T> void command_crafter$onMissingPossibleKey(MapLike<T> input, ExtraDecoderBehavior<T> extraBehavior) {
        // Suppress any unknown key warnings for the input, since the proper decoder is unknown
        input.entries().forEach(entry -> extraBehavior.markCompletelyAccessed(entry.getSecond()));
    }
}
