package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import kotlin.jvm.functions.Function1;
import net.minecraft.util.StringRepresentable;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringIdentifiableNameTransformerConsumer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(StringRepresentable.StringRepresentableCodec.class)
public class StringIdentifiableBasicCodecMixin<S> implements StringIdentifiableNameTransformerConsumer {

    private StringRepresentable[] command_crafter$values;
    private Function1<? super String, String> command_crafter$nameTransformer;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$saveValues(StringRepresentable[] values, Function<String, S> idToIdentifiable, ToIntFunction<S> identifiableToOrdinal, CallbackInfo ci) {
        command_crafter$values = values;
    }

    @Inject(
            method = "decode",
            at = @At("HEAD")
    )
    private <T> void command_crafter$addSuggestions(DynamicOps<T> ops, T input, CallbackInfoReturnable<DataResult<Pair<S, T>>> cir) {
        //noinspection unchecked
        final var analyzingOps = (StringRangeTree.AnalyzingDynamicOps<T>)getOrNull(StringRangeTree.AnalyzingDynamicOps.Companion.getCURRENT_ANALYZING_OPS());
        if(analyzingOps == null) return;
        analyzingOps.getNodeStartSuggestions(input).add(() ->
                Arrays.stream(command_crafter$values).map(value -> {
                    var string = value.getSerializedName();
                    if (command_crafter$nameTransformer != null)
                        string = command_crafter$nameTransformer.invoke(string);
                    return new StringRangeTree.Suggestion<>(ops.createString(string));
                })
        );
    }

    @Override
    public void command_crafter$setNameTransformer(@NotNull Function1<? super String, String> nameTransformer) {
        command_crafter$nameTransformer = nameTransformer;
    }
}
