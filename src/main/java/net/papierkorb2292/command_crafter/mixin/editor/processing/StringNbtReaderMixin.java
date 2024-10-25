package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.nbt.*;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.NbtSemanticTokenProvider;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

@Mixin(StringNbtReader.class)
public abstract class StringNbtReaderMixin implements StringRangeTreeCreator<NbtElement>, AllowMalformedContainer {
    @Shadow @Final private StringReader reader;

    @Shadow public abstract NbtCompound parseCompound() throws CommandSyntaxException;

    @Shadow protected abstract NbtElement parseList() throws CommandSyntaxException;

    private @Nullable StringRangeTree.Builder<NbtElement> command_crafter$stringRangeTreeBuilder;
    private NbtCompound command_crafter$currentParsingCompound = null;
    private NbtList command_crafter$currentParsingNbtList = null;
    private ArrayList<?> command_crafter$currentParsingPrimitiveArray = null;
    private int command_crafter$currentNestedStartChar = -1;
    private boolean command_crafter$skipFirstNestedChar = false;
    private boolean command_crafter$allowMalformed = false;

    private Deque<Integer> command_crafter$elementAllowedStartCursor = new LinkedList<>();

    public StringNbtReaderMixin(StringReader reader) {
        command_crafter$elementAllowedStartCursor.push(0);
    }

    @Override
    public void command_crafter$setAllowMalformed(boolean allowMalformed) {
        command_crafter$allowMalformed = allowMalformed;
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
        } else if(element instanceof NbtFloat nbtFloat) {
            element = NbtFloatAccessor.callInit(nbtFloat.floatValue());
        } else if(element instanceof NbtDouble nbtDouble) {
            element = NbtDoubleAccessor.callInit(nbtDouble.doubleValue());
        } else if(element instanceof NbtString nbtString && nbtString.asString().isEmpty()) {
            element = NbtStringAccessor.callInit("");
        }

        command_crafter$stringRangeTreeBuilder.addNode(element, new StringRange(startCursor, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
        return element;
    }

    @WrapOperation(
            method = "parseElementPrimitive",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;readQuotedString()Ljava/lang/String;",
                    remap = false
            )
    )
    private String command_crafter$allowMalformedString(StringReader instance, Operation<String> original) {
        if(!command_crafter$allowMalformed)
            return original.call(instance);
        final var startChar = reader.getCursor();
        try {
            return MixinUtil.<String, CommandSyntaxException>callWithThrows(original, instance);
        } catch(CommandSyntaxException e) {
            return reader.getString().substring(startChar, reader.getCursor());
        }
    }

    @WrapOperation(
            method = "parseCompound",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readComma()Z"
            )
    )
    private boolean command_crafter$addCompoundRangeBetweenEntriesToStringRangeTree(StringNbtReader instance, Operation<Boolean> original, @Local NbtCompound compound) {
        if(command_crafter$stringRangeTreeBuilder == null)
            return original.call(instance);
        reader.skipWhitespace();
        var entryEnd = reader.getCursor() + 1;
        if(original.call(instance)) {
            command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(compound, new StringRange(entryEnd, reader.getCursor()));
            return true;
        }
        return false;
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
                    target = "Lnet/minecraft/nbt/StringNbtReader;readArray(Lnet/minecraft/nbt/NbtType;Lnet/minecraft/nbt/NbtType;)Ljava/util/List;"
            )
    )
    private void command_crafter$registerArrayPlaceholder(CallbackInfoReturnable<NbtElement> cir, @Share("nbtArrayPlaceholderReplacer") LocalRef<Function1<NbtElement, Unit>> nbtArrayPlaceholderReplacer) {
        if(command_crafter$stringRangeTreeBuilder == null) return;
        nbtArrayPlaceholderReplacer.set(command_crafter$stringRangeTreeBuilder.registerNodeOrderPlaceholder());
    }

    @ModifyReturnValue(
            method = "parseElementPrimitiveArray",
            at = @At("RETURN")
    )
    private NbtElement command_crafter$addArrayToStringRangeTree(NbtElement array, @Local int startCursor, @Share("nbtArrayPlaceholderReplacer") LocalRef<Function1<NbtElement, Unit>> nbtArrayPlaceholderReplacer) {
        if(command_crafter$stringRangeTreeBuilder == null) return array;
        nbtArrayPlaceholderReplacer.get().invoke(array);
        command_crafter$stringRangeTreeBuilder.addNode(array, new StringRange(startCursor - 1, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
        return array;
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
            command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(list, new StringRange(entryEnd, reader.getCursor()));
        }
        return list;
    }

    @WrapMethod(
            method = "parseCompound"
    )
    private NbtCompound command_crafter$addCompoundStringRangeAndAllowMalformedCompound(Operation<NbtCompound> original) throws CommandSyntaxException {
        if(!command_crafter$allowMalformed) {
            final var startChar = reader.getCursor();
            final var result = original.call();
            if(command_crafter$stringRangeTreeBuilder != null) {
                command_crafter$elementAllowedStartCursor.pop();
                command_crafter$stringRangeTreeBuilder.addNode(result, new StringRange(startChar, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
            }
            return result;
        }
        final var startChar = command_crafter$currentNestedStartChar != -1 ? command_crafter$currentNestedStartChar : reader.getCursor();
        command_crafter$currentNestedStartChar = -1;
        final var resultCompound = command_crafter$currentParsingCompound != null ? command_crafter$currentParsingCompound : new NbtCompound();
        command_crafter$currentParsingCompound = resultCompound;
        try {
            // Will add to `currentParsingCompound` because of method `initCompound`
            MixinUtil.<NbtCompound, CommandSyntaxException>callWithThrows(original);
            if(command_crafter$stringRangeTreeBuilder != null)
                command_crafter$elementAllowedStartCursor.pop();
        } catch(CommandSyntaxException e) {
            if(command_crafter$stringRangeTreeBuilder != null && command_crafter$currentParsingCompound == null) {
                // Compound was consumed, so an allowedStartCursor was pushed by 'initCompound'
                command_crafter$elementAllowedStartCursor.pop();
            }
            command_crafter$currentParsingCompound = null;
            if(reader.getCursor() != startChar) {
                // A '{' was found
                // Skip to next entry or end of compound
                while(reader.canRead() && reader.peek() != '}' && reader.peek() != ',')
                    reader.skip();
                if(reader.canRead()) {
                    if(reader.peek() == ',')
                        reader.skip();

                    // Continue parsing compound
                    command_crafter$skipFirstNestedChar = true;
                    command_crafter$currentNestedStartChar = startChar;
                    command_crafter$currentParsingCompound = resultCompound;
                    // Will add to `currentParsingCompound` because of method `initCompound`
                    parseCompound();
                    // Skip adding to tree, because it was already added in the recursive call
                    return resultCompound;
                }
            }
        }
        if (command_crafter$stringRangeTreeBuilder != null)
            command_crafter$stringRangeTreeBuilder.addNode(resultCompound, new StringRange(startChar, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
        return resultCompound;
    }

    @ModifyExpressionValue(
            method = "parseCompound",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/nbt/NbtCompound"
            )
    )
    private NbtCompound command_crafter$initCompound(NbtCompound value) {
        if(command_crafter$allowMalformed && command_crafter$currentParsingCompound != null) {
            value = command_crafter$currentParsingCompound;
            // Don't use the same compound when parsing children
            command_crafter$currentParsingCompound = null;
        }
        if(command_crafter$stringRangeTreeBuilder != null) {
            command_crafter$stringRangeTreeBuilder.addNodeOrder(value);
            final var rangeBetweenInternalNodeEntriesStart = reader.getCursor();
            reader.skipWhitespace();
            command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(value, new StringRange(rangeBetweenInternalNodeEntriesStart, reader.getCursor()));
            command_crafter$elementAllowedStartCursor.push(command_crafter$elementAllowedStartCursor.peek());
        }
        return value;
    }

    @WrapMethod(
            method = "parseList"
    )
    private NbtElement command_crafter$addListStringRangeAndAllowMalformedList(Operation<NbtElement> original) throws CommandSyntaxException {
        if(!command_crafter$allowMalformed) {
            final var startChar = reader.getCursor();
            final var result = original.call();
            if(command_crafter$stringRangeTreeBuilder != null) {
                command_crafter$elementAllowedStartCursor.pop();
                command_crafter$stringRangeTreeBuilder.addNode(result, new StringRange(startChar, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
            }
            return result;
        }
        final var startChar = command_crafter$currentNestedStartChar != -1 ? command_crafter$currentNestedStartChar : reader.getCursor();
        command_crafter$currentNestedStartChar = -1;
        final var resultList = command_crafter$currentParsingNbtList != null ? command_crafter$currentParsingNbtList : new NbtList();
        command_crafter$currentParsingNbtList = resultList;
        try {
            // Will add to `currentParsingList` because of method `initNbtList`
            MixinUtil.<NbtElement, CommandSyntaxException>callWithThrows(original);
            if(command_crafter$stringRangeTreeBuilder != null)
                command_crafter$elementAllowedStartCursor.pop();
        } catch(CommandSyntaxException e) {
            if(command_crafter$stringRangeTreeBuilder != null && command_crafter$currentParsingNbtList == null) {
                // List was consumed, so an allowedStartCursor was pushed by 'initNbtList'
                command_crafter$elementAllowedStartCursor.pop();
            }
            command_crafter$currentParsingNbtList = null;
            if(reader.getCursor() != startChar) {
                // A '[' was found
                // Skip to next entry or end of list
                while(reader.canRead() && reader.peek() != ']' && reader.peek() != ',')
                    reader.skip();
                if(reader.canRead()) {
                    if(reader.peek() == ',')
                        reader.skip();

                    // Continue parsing list
                    command_crafter$skipFirstNestedChar = true;
                    command_crafter$currentNestedStartChar = startChar;
                    command_crafter$currentParsingNbtList = resultList;
                    // Will add to `currentParsingList` because of method `initNbtList`
                    parseList();
                    // Skip adding to tree, because it was already added in the recursive call
                    return resultList;
                }
            }
        }
        if (command_crafter$stringRangeTreeBuilder != null)
            command_crafter$stringRangeTreeBuilder.addNode(resultList, new StringRange(startChar, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
        return resultList;
    }

    @ModifyExpressionValue(
            method = "parseList",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/nbt/NbtList"
            )
    )
    private NbtList command_crafter$initNbtList(NbtList value) {
        if(command_crafter$allowMalformed && command_crafter$currentParsingNbtList != null) {
            value = command_crafter$currentParsingNbtList;
            // 'currentParsingNbtList' is reset in 'setListTypeWhenAddingToExistingList'
        }
        if(command_crafter$stringRangeTreeBuilder != null) {
            command_crafter$stringRangeTreeBuilder.addNodeOrder(value);
            command_crafter$elementAllowedStartCursor.push(command_crafter$elementAllowedStartCursor.peek());
        }
        return value;
    }

    @ModifyVariable(
            method = "parseList",
            at = @At("STORE")
    )
    private NbtType<?> command_crafter$setListTypeWhenAddingToExistingList(NbtType<?> type) {
        if(command_crafter$currentParsingNbtList != null) {
            type = NbtTypes.byId(command_crafter$currentParsingNbtList.getHeldType());
            // Don't use the same compound when parsing children
            command_crafter$currentParsingNbtList = null;
        }
        return type;
    }

    @WrapWithCondition(
            method = { "parseList", "readArray" },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;setCursor(I)V",
                    remap = false
            )
    )
    private boolean command_crafter$keepCursorOnMalformedList(StringReader instance, int cursor) {
        return !command_crafter$allowMalformed;
    }

    @WrapMethod(
            method = "parseElementPrimitiveArray"
    )
    private NbtElement command_crafter$allowMalformedPrimitiveArrayHeader(Operation<NbtElement> original) throws CommandSyntaxException {
        if(!command_crafter$allowMalformed)
            return original.call();
        try {
            return MixinUtil.<NbtElement, CommandSyntaxException>callWithThrows(original);
        } catch(CommandSyntaxException e) {
            // Error happened when reading header, because other errors are caught by 'addArrayStringRangeAndAllowMalformedArray'
            // Try parsing as a list instead so contents of the array can still be analyzed
            reader.setCursor(reader.getCursor() + 2);
            command_crafter$skipFirstNestedChar = true;
            return parseList();
        }
    }

    @ModifyExpressionValue(
            method = "readArray",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/Lists;newArrayList()Ljava/util/ArrayList;",
                    remap = false
            )
    )
    private <T> ArrayList<T> command_crafter$initPrimitiveArray(ArrayList<T> value) {
        if(command_crafter$allowMalformed && command_crafter$currentParsingPrimitiveArray != null) {
            //noinspection unchecked
            value = (ArrayList<T>) command_crafter$currentParsingPrimitiveArray;
            // Don't use the same compound when parsing children
            command_crafter$currentParsingPrimitiveArray = null;
        }
        if(command_crafter$stringRangeTreeBuilder != null) {
            command_crafter$elementAllowedStartCursor.push(command_crafter$elementAllowedStartCursor.peek());
        }
        return value;
    }

    @WrapMethod(
            method = "readArray"
    )
    private <T> List<T> command_crafter$addArrayStringRangeAndAllowMalformedArray(NbtType<?> arrayTypeReader, NbtType<?> typeReader, Operation<List<T>> original) throws CommandSyntaxException {
        if(!command_crafter$allowMalformed) {
            var result = original.call(arrayTypeReader, typeReader);
            if(command_crafter$stringRangeTreeBuilder != null)
                command_crafter$elementAllowedStartCursor.pop();
            return result;
        }
        //noinspection unchecked
        final ArrayList<T> resultArray = command_crafter$currentParsingPrimitiveArray != null ? (ArrayList<T>) command_crafter$currentParsingPrimitiveArray : Lists.newArrayList();
        command_crafter$currentParsingPrimitiveArray = resultArray;
        try {
            // Will add to `currentParsingPrimitiveArray` because of method `initPrimitiveArray`
            MixinUtil.<List<T>, CommandSyntaxException>callWithThrows(original, arrayTypeReader, typeReader);
            if(command_crafter$stringRangeTreeBuilder != null)
                command_crafter$elementAllowedStartCursor.pop();
        } catch(CommandSyntaxException e) {
            if(command_crafter$stringRangeTreeBuilder != null && command_crafter$currentParsingPrimitiveArray == null) {
                // Array was consumed, so an allowedStartCursor was pushed by 'initPrimitiveArray'
                command_crafter$elementAllowedStartCursor.pop();
            }
            command_crafter$currentParsingPrimitiveArray = null;
            // Skip to next entry or end of array
            while(reader.canRead() && reader.peek() != ']' && reader.peek() != ',')
                reader.skip();
            if(reader.canRead()) {
                if(reader.peek() == ',')
                    reader.skip();

                // Continue parsing array
                command_crafter$currentParsingPrimitiveArray = resultArray;
                // Will add to `currentParsingPrimitiveArray` because of method `initPrimitiveArray`
                parseList();
            }
        }
        return resultArray;
    }

    @WrapWithCondition(
            method = { "parseCompound", "parseList", "parseElementPrimitiveArray" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;expect(C)V",
                    ordinal = 0
            )
    )
    private boolean isCommand_crafter$skipFirstNestedCharWhenRetryingParsing(StringNbtReader instance, char c) {
        final var shouldSkip = command_crafter$skipFirstNestedChar;
        command_crafter$skipFirstNestedChar = false;
        return !shouldSkip;
    }
}
