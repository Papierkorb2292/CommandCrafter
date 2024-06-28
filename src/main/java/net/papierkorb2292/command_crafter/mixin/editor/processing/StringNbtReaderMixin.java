package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import net.minecraft.nbt.*;
import net.papierkorb2292.command_crafter.editor.processing.NbtSemanticTokenProvider;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

@Mixin(StringNbtReader.class)
public class StringNbtReaderMixin implements StringRangeTreeCreator<NbtElement> {
    @Shadow @Final private StringReader reader;

    private @Nullable StringRangeTree.Builder<NbtElement> command_crafter$stringRangeTreeBuilder;
    private ThreadLocal<AbstractNbtList<?>> command_crafter$preInstantiatedNbtArray = new ThreadLocal<>();

    private Deque<Integer> command_crafter$elementAllowedStartCursor = new LinkedList<>();

    public StringNbtReaderMixin(StringReader reader) {
        command_crafter$elementAllowedStartCursor.push(0);
    }

    @Override
    public void command_crafter$setStringRangeTreeBuilder(@NotNull StringRangeTree.Builder<NbtElement> builder) {
        command_crafter$stringRangeTreeBuilder = builder;
    }

    @ModifyReturnValue(
            method = "parseElementPrimitive",
            at = @At(
                    value = "RETURN"
            )
    )
    public NbtElement command_crafter$addPrimitiveToStringRangeTree(NbtElement element, @Local int startCursor) {
        if(command_crafter$stringRangeTreeBuilder == null)
            return element;

        // Circumvent instance caching so StringRangeTree maps work correctly and
        // keep track of which NbtBytes came from a 'true' or 'false' keyword.
        if(element instanceof NbtByte nbtByte) {
            var startChar = reader.getString().charAt(startCursor);
            if(startChar == 't' || startChar == 'f') {
                element = new NbtSemanticTokenProvider.NbtBoolean(nbtByte.byteValue() != 0);
            } else {
                element = NbtByteAccessor.callInit(nbtByte.byteValue());
            }
        } else if(element instanceof NbtLong nbtLong) {
            element = NbtLongAccessor.callInit(nbtLong.longValue());
        } else if(element instanceof NbtInt nbtInt) {
            element = NbtIntAccessor.callInit(nbtInt.intValue());
        } else if(element instanceof NbtShort nbtShort) {
            element = NbtShortAccessor.callInit(nbtShort.shortValue());
        }

        command_crafter$stringRangeTreeBuilder.addNode(element, new StringRange(startCursor, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
        return element;
    }

    @ModifyExpressionValue(
            method = "parseCompound",
            at = @At(
                    value = "NEW",
                    target = "()Lnet/minecraft/nbt/NbtCompound;"
            )
    )
    private NbtCompound command_crafter$addCompoundOrderToStringRangeTree(NbtCompound compound, @Share("compoundStartCursor") LocalIntRef compoundStartCursor) {
        if(command_crafter$stringRangeTreeBuilder != null) {
            compoundStartCursor.set(reader.getCursor() - 1);
            command_crafter$stringRangeTreeBuilder.addNodeOrder(compound);
            command_crafter$elementAllowedStartCursor.push(command_crafter$elementAllowedStartCursor.peek());
        }
        return compound;
    }

    @ModifyReturnValue(
            method = "parseCompound",
            at = @At("RETURN")
    )
    private NbtCompound command_crafter$addCompoundToStringRangeTree(NbtCompound compound, @Share("compoundStartCursor") LocalIntRef compoundStartCursor) {
        if(command_crafter$stringRangeTreeBuilder != null) {
            command_crafter$elementAllowedStartCursor.pop();
            command_crafter$stringRangeTreeBuilder.addNode(compound, new StringRange(compoundStartCursor.get(), reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
        }
        return compound;
    }

    @ModifyVariable(
            method = "parseCompound",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;getCursor()I",
                    remap = false
            )
    )
    private NbtCompound command_crafter$addCompoundRangeBetweenEntriesToStringRangeTree(NbtCompound compound) {
        if (command_crafter$stringRangeTreeBuilder != null) {
            var entryEnd = reader.getCursor();
            reader.skipWhitespace();
            command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(compound, new StringRange(entryEnd, reader.getCursor()));
        }
        return compound;
    }

    @ModifyVariable(
            method = "parseCompound",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/String;isEmpty()Z"
            )
    )
    private NbtCompound command_crafter$addCompoundTagToStringRangeTree(NbtCompound compound, @Local int startCursor) {
        if (command_crafter$stringRangeTreeBuilder != null) {
            // startCursor has skipped whitespaces before the name because of addCompoundRangeBetweenRangeToStringRangeTree
            command_crafter$stringRangeTreeBuilder.addMapKeyRange(compound, new StringRange(startCursor, reader.getCursor()));
            command_crafter$elementAllowedStartCursor.pop();
            command_crafter$elementAllowedStartCursor.push(reader.getCursor() + 1);
        }
        return compound;
    }

    @ModifyExpressionValue(
            method = "parseList",
            at = @At(
                    value = "NEW",
                    target = "()Lnet/minecraft/nbt/NbtList;"
            )
    )
    private NbtList command_crafter$addListOrderToStringRangeTree(NbtList list, @Share("listStartCursor") LocalIntRef listStartCursor) {
        if(command_crafter$stringRangeTreeBuilder != null) {
            listStartCursor.set(reader.getCursor() - 1);
            command_crafter$stringRangeTreeBuilder.addNodeOrder(list);
            command_crafter$elementAllowedStartCursor.push(command_crafter$elementAllowedStartCursor.peek());
        }
        return list;
    }

    @ModifyReturnValue(
            method = "parseList",
            at = @At("RETURN")
    )
    private NbtElement command_crafter$addListToStringRangeTree(NbtElement list, @Share("listStartCursor") LocalIntRef listStartCursor) {
        if(command_crafter$stringRangeTreeBuilder != null) {
            command_crafter$elementAllowedStartCursor.pop();
            command_crafter$stringRangeTreeBuilder.addNode(list, new StringRange(listStartCursor.get(), reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
        }
        return list;
    }

    @ModifyVariable(
            method = "parseList",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;getCursor()I",
                    remap = false
            )
    )
    private NbtList command_crafter$addListRangeBetweenEntriesToStringRangeTree(NbtList list) {
        if (command_crafter$stringRangeTreeBuilder != null) {
            var entryEnd = reader.getCursor();
            reader.skipWhitespace();
            command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(list, new StringRange(entryEnd, reader.getCursor()));
            command_crafter$elementAllowedStartCursor.pop();
            command_crafter$elementAllowedStartCursor.push(entryEnd);
        }
        return list;
    }

    @Inject(
            method = "parseElementPrimitiveArray",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readArray(Lnet/minecraft/nbt/NbtType;Lnet/minecraft/nbt/NbtType;)Ljava/util/List;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=66" //char = 'B'
                    )
            )
    )
    private void command_crafter$preInstantiateByteArray(CallbackInfoReturnable<NbtElement> cir) {
        if(command_crafter$stringRangeTreeBuilder == null) return;
        var array = new NbtByteArray(new byte[0]);
        command_crafter$stringRangeTreeBuilder.addNodeOrder(array);
        command_crafter$preInstantiatedNbtArray.set(array);
    }

    @Inject(
            method = "parseElementPrimitiveArray",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readArray(Lnet/minecraft/nbt/NbtType;Lnet/minecraft/nbt/NbtType;)Ljava/util/List;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=76" //char = 'L'
                    )
            )
    )
    private void command_crafter$preInstantiateLongArray(CallbackInfoReturnable<NbtElement> cir) {
        if(command_crafter$stringRangeTreeBuilder == null) return;
        var array = new NbtLongArray(new long[0]);
        command_crafter$stringRangeTreeBuilder.addNodeOrder(array);
        command_crafter$preInstantiatedNbtArray.set(array);
    }

    @Inject(
            method = "parseElementPrimitiveArray",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readArray(Lnet/minecraft/nbt/NbtType;Lnet/minecraft/nbt/NbtType;)Ljava/util/List;",
                    ordinal = 0
            ),
            slice = @Slice(
                    from = @At(
                            value = "CONSTANT",
                            args = "intValue=73" //char = 'I'
                    )
            )
    )
    private void command_crafter$preInstantiateIntArray(CallbackInfoReturnable<NbtElement> cir) {
        if(command_crafter$stringRangeTreeBuilder == null) return;
        var array = new NbtIntArray(new int[0]);
        command_crafter$stringRangeTreeBuilder.addNodeOrder(array);
        command_crafter$preInstantiatedNbtArray.set(array);
    }

    @WrapOperation(
            method = "parseElementPrimitiveArray",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/util/List;)Lnet/minecraft/nbt/NbtByteArray;"
            )
    )
    private NbtByteArray command_crafter$fillPreInstantiatedByteArray(List<Byte> content, Operation<NbtByteArray> op) {
        var nbtArray = (NbtByteArray) command_crafter$preInstantiatedNbtArray.get();
        if(nbtArray == null) return op.call(content);
        for(var b : content) nbtArray.add(NbtByte.of(b));
        return nbtArray;
    }

    @WrapOperation(
            method = "parseElementPrimitiveArray",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/util/List;)Lnet/minecraft/nbt/NbtIntArray;"
            )
    )
    private NbtIntArray command_crafter$fillPreInstantiatedIntArray(List<Integer> content, Operation<NbtIntArray> op) {
        var nbtArray = (NbtIntArray) command_crafter$preInstantiatedNbtArray.get();
        if(nbtArray == null) return op.call(content);
        for(var b : content) nbtArray.add(NbtInt.of(b));
        return nbtArray;
    }

    @WrapOperation(
            method = "parseElementPrimitiveArray",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/util/List;)Lnet/minecraft/nbt/NbtLongArray;"
            )
    )
    private NbtLongArray command_crafter$fillPreInstantiatedLongArray(List<Long> content, Operation<NbtLongArray> op) {
        var nbtArray = (NbtLongArray) command_crafter$preInstantiatedNbtArray.get();
        if(nbtArray == null) return op.call(content);
        for(var b : content) nbtArray.add(NbtLong.of(b));
        return nbtArray;
    }

    @ModifyVariable(
            method = "parseList",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;getCursor()I",
                    remap = false
            )
    )
    private NbtList command_crafter$addArrayRangeBetweenEntriesToStringRangeTree(NbtList list) {
        if (command_crafter$stringRangeTreeBuilder != null) {
            var entryEnd = reader.getCursor();
            reader.skipWhitespace();
            command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(command_crafter$preInstantiatedNbtArray.get(), new StringRange(entryEnd, reader.getCursor()));
        }
        return list;
    }
}
