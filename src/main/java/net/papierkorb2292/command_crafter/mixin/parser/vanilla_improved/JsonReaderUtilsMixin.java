package net.papierkorb2292.command_crafter.mixin.parser.vanilla_improved;

import com.google.gson.stream.JsonReader;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.JsonReaderUtils;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Reader;

@Mixin(JsonReaderUtils.class)
public class JsonReaderUtilsMixin {

    @Inject(
            method = "parse",
            at = @At("RETURN")
    )
    private static <T> void command_crafter$preventSkipOfFollowingWhitespace(RegistryWrapper.WrapperLookup registryLookup, StringReader reader, Codec<T> codec, CallbackInfoReturnable<T> cir) {
        if(VanillaLanguage.Companion.isReaderVanilla(reader) && (!reader.canRead(0) || Character.isWhitespace(reader.peek(-1)))) {
            reader.setCursor(reader.getCursor() - 1);
        }
    }

    @WrapOperation(
            method = "parse",
            at = @At(
                    value = "NEW",
                    target = "com/google/gson/stream/JsonReader"
            ),
            remap = false
    )
    private static JsonReader command_crafter$allowMultiline(Reader in, Operation<JsonReader> op, @Local(argsOnly = true) StringReader stringReader, @Share("copiedDirectiveStringReader") LocalRef<DirectiveStringReader<?>> copiedDirectiveStringReader) {
        if(VanillaLanguage.Companion.isReaderVanilla(stringReader)) {
            var readerCopy = ((DirectiveStringReader<?>) stringReader).copy();
            copiedDirectiveStringReader.set(readerCopy);
            return new JsonReader(readerCopy.asReader());
        }
        return op.call(in);
    }

    @ModifyExpressionValue(
            method = "parse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/JsonReaderUtils;getPos(Lcom/google/gson/stream/JsonReader;)I"
            )
    )
    private static int command_crafter$updateCursorForDirectiveStringReader(int pos, @Local(argsOnly = true) StringReader reader, @Local JsonReader jsonReader, @Share("copiedDirectiveStringReader") LocalRef<DirectiveStringReader<?>> copiedDirectiveStringReader) {
        if(copiedDirectiveStringReader.get() != null) {
            var copiedReader = copiedDirectiveStringReader.get();
            return copiedReader.getCursor() - reader.getCursor() + ((JsonReaderAccessor)jsonReader).getPos() - ((JsonReaderAccessor)jsonReader).getLimit();
        }
        return pos;
    }
}
