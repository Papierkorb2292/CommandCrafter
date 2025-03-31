package net.papierkorb2292.command_crafter.mixin.editor.debugger;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.brigadier.context.StringRange;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.registry.tag.TagEntry;
import net.papierkorb2292.command_crafter.editor.debugger.helper.StringRangeContainer;
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.tags.FunctionTagDebugHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static net.papierkorb2292.command_crafter.helper.UtilKt.getOrNull;

@Mixin(TagEntry.class)
public class TagEntryMixin implements StringRangeContainer {

    private StringRange command_crafter$fileRange;

    @ModifyExpressionValue(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;xmap(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;",
                    remap = false
            )
    )
    private static Codec<TagEntry> command_crafter$storeElementStringRange(Codec<TagEntry> codec) {
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<TagEntry, T>> decode(DynamicOps<T> ops, T input) {
                return codec.decode(ops, input).map(pair -> {
                    var rangeMap = getOrNull(FunctionTagDebugHandler.Companion.getTAG_PARSING_ELEMENT_RANGES());
                    if (rangeMap != null) {
                        ((StringRangeContainer)pair.getFirst()).command_crafter$setRange(rangeMap.get(input));
                    }
                    return pair;
                });
            }

            @Override
            public <T> DataResult<T> encode(TagEntry input, DynamicOps<T> ops, T prefix) {
                return codec.encode(input, ops, prefix);
            }
        };
    }

    @Nullable
    @Override
    public StringRange command_crafter$getRange() {
        return command_crafter$fileRange;
    }

    @Override
    public void command_crafter$setRange(@NotNull StringRange range) {
        command_crafter$fileRange = range;
    }
}
