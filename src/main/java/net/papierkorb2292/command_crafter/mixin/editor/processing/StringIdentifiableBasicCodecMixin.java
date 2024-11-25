package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.util.StringIdentifiable;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;
import java.util.function.ToIntFunction;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(StringIdentifiable.BasicCodec.class)
public class StringIdentifiableBasicCodecMixin<S> {

    private StringIdentifiable[] command_crafter$values;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$saveValues(StringIdentifiable[] values, Function<String, S> idToIdentifiable, ToIntFunction<S> identifiableToOrdinal, CallbackInfo ci) {
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
        final var suggestions = analyzingOps.getNodeStartSuggestions(input);
        for(final var value : command_crafter$values) {
            suggestions.add(new StringRangeTree.Suggestion<>(ops.createString(value.asString())));
        }
    }
}
