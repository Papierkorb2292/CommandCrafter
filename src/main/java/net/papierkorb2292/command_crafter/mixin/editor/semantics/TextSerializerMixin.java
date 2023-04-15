package net.papierkorb2292.command_crafter.mixin.editor.semantics;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.StringReader;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Text.Serializer.class)
public abstract class TextSerializerMixin {

    @Shadow
    private static int getPosition(JsonReader reader) { throw new AssertionError(); }

    @SuppressWarnings("unused")
    @WrapOperation(
            method = "fromJson(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/text/MutableText;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/gson/TypeAdapter;read(Lcom/google/gson/stream/JsonReader;)Ljava/lang/Object;"
            )
    )
    private static Object command_crafter$advanceStringReaderOnError(TypeAdapter<?> adapter, JsonReader jsonReader, Operation<Object> op, StringReader stringReader) {
        try {
            return op.call(adapter, jsonReader);
        } catch (Exception e) {
            stringReader.setCursor(stringReader.getCursor() + getPosition(jsonReader));
            if(!stringReader.canRead(0) || Character.isWhitespace(stringReader.peek(-1))) {
                stringReader.setCursor(stringReader.getCursor() - 1);
            }
            throw e;
        }
    }
}
