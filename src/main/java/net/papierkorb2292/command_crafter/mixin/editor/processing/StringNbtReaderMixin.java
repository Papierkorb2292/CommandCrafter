package net.papierkorb2292.command_crafter.mixin.editor.processing;

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
import net.papierkorb2292.command_crafter.editor.processing.NbtStringRangeListBuilder;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
    private NbtStringRangeListBuilder command_crafter$currentParsingNbtListBuilder = null;
    private int command_crafter$currentNestedStartChar = -1;
    private boolean command_crafter$skipFirstNestedChar = false;
    private boolean command_crafter$allowMalformed = false;
    private List<StringRange> command_crafter$pendingListRangesBetweenInternalNodeEntries = new ArrayList<>();

    private Deque<Integer> command_crafter$elementAllowedStartCursor = new LinkedList<>();

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void command_crafter$pushElementAllowedStartCursor(StringReader reader, CallbackInfo ci) {
        command_crafter$elementAllowedStartCursor.push(reader.getCursor());
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

        // Circumvent instance caching so StringRangeTree maps work correctly
        if(element instanceof NbtByte nbtByte) {
            element = NbtByteAccessor.callInit(nbtByte.byteValue());
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
    private NbtList command_crafter$addListRangeBetweenEntriesToStringRangeTree(NbtList list, @Share("listBuilder") LocalRef<NbtStringRangeListBuilder> listBuilderRef) {
        if (command_crafter$stringRangeTreeBuilder != null) {
            var entryEnd = reader.getCursor();
            reader.skipWhitespace();
            final var rangeBetweenEntries = new StringRange(entryEnd, reader.getCursor());
            if(listBuilderRef.get() != null) {
                listBuilderRef.get().addRangeBetweenEntries(rangeBetweenEntries);
            } else {
                command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(list, rangeBetweenEntries);
            }
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
    private void command_crafter$registerArrayPlaceholder(CallbackInfoReturnable<NbtElement> cir, @Local int listStartCursor, @Share("nbtArrayPlaceholderReplacer") LocalRef<Function1<NbtElement, Unit>> nbtArrayPlaceholderReplacer) {
        if(command_crafter$stringRangeTreeBuilder == null) return;
        nbtArrayPlaceholderReplacer.set(command_crafter$stringRangeTreeBuilder.registerNodeOrderPlaceholder());
        command_crafter$pendingListRangesBetweenInternalNodeEntries.add(new StringRange(listStartCursor + 2, reader.getCursor()));
    }

    @ModifyReturnValue(
            method = "parseElementPrimitiveArray",
            at = @At("RETURN")
    )
    private NbtElement command_crafter$addArrayToStringRangeTree(NbtElement array, @Local int startCursor, @Share("nbtArrayPlaceholderReplacer") LocalRef<Function1<NbtElement, Unit>> nbtArrayPlaceholderReplacer) {
        if(command_crafter$stringRangeTreeBuilder == null) return array;
        nbtArrayPlaceholderReplacer.get().invoke(array);
        command_crafter$stringRangeTreeBuilder.addNode(array, new StringRange(startCursor - 1, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
        for(final var range : command_crafter$pendingListRangesBetweenInternalNodeEntries) {
            command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(array, range);
        }
        command_crafter$pendingListRangesBetweenInternalNodeEntries.clear();
        return array;
    }

    @WrapOperation(
            method = "readArray",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readComma()Z"
            )
    )
    private boolean command_crafter$saveArrayRangeBetweenEntries(StringNbtReader instance, Operation<Boolean> op) {
        if(command_crafter$stringRangeTreeBuilder == null)
            return op.call(instance);

        reader.skipWhitespace();
        var entryEnd = reader.getCursor() + 1;
        if(op.call(instance)) {
            command_crafter$pendingListRangesBetweenInternalNodeEntries.add(new StringRange(entryEnd, reader.getCursor()));
            return true;
        }
        return false;
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
                    final var c = reader.read();
                    if(c == ',') {
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

    @WrapOperation(
            method = "parseCompound",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseElement()Lnet/minecraft/nbt/NbtElement;"
            )
    )
    private NbtElement command_crafter$addErroringCompoundEntry(StringNbtReader instance, Operation<NbtElement> original, @Local NbtCompound compound, @Local String tag) throws CommandSyntaxException {
        if (!command_crafter$allowMalformed) {
            return original.call(instance);
        }
        final var startChar = reader.getCursor();
        try {
            return MixinUtil.<NbtElement, CommandSyntaxException>callWithThrows(original, instance);
        } catch(CommandSyntaxException e) {
            final var entry = NbtEndAccessor.callInit();
            compound.put(tag, entry);
            if(command_crafter$stringRangeTreeBuilder != null)
                command_crafter$stringRangeTreeBuilder.addNode(entry, new StringRange(startChar, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
            throw e;
        }
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
        final var resultListBuilder = command_crafter$currentParsingNbtListBuilder != null
                ? command_crafter$currentParsingNbtListBuilder
                : (command_crafter$stringRangeTreeBuilder != null
                    ? NbtStringRangeListBuilder.Companion.forStringRangeTreeBuilderAtCurrentNodeOrder(command_crafter$stringRangeTreeBuilder)
                    : NbtStringRangeListBuilder.Companion.forNoStringRangeTree());
        command_crafter$currentParsingNbtListBuilder = resultListBuilder;
        try {
            // Will add to `currentParsingListBuilder` because of method `initNbtList`
            MixinUtil.<NbtElement, CommandSyntaxException>callWithThrows(original);
            if(command_crafter$stringRangeTreeBuilder != null)
                command_crafter$elementAllowedStartCursor.pop();
        } catch(CommandSyntaxException e) {
            if(command_crafter$stringRangeTreeBuilder != null && command_crafter$currentParsingNbtListBuilder == null) {
                // List was consumed, so an allowedStartCursor was pushed by 'initNbtList'
                command_crafter$elementAllowedStartCursor.pop();
            }
            command_crafter$currentParsingNbtListBuilder = null;
            if(reader.getCursor() != startChar) {
                // A '[' was found
                // Skip to next entry or end of list
                while(reader.canRead() && reader.peek() != ']' && reader.peek() != ',')
                    reader.skip();
                if(reader.canRead()) {
                    final var c = reader.read();
                    if(c == ',') {
                        // Continue parsing list
                        command_crafter$skipFirstNestedChar = true;
                        command_crafter$currentNestedStartChar = startChar;
                        command_crafter$currentParsingNbtListBuilder = resultListBuilder;
                        // Will add to `currentParsingListBuilder` because of method `initNbtList`
                        return parseList();
                    }
                }
            }
        }
        return resultListBuilder.build(new StringRange(startChar, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
    }

    @Inject(
            method = "parseList",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/nbt/StringNbtReader;EXPECTED_VALUE:Lcom/mojang/brigadier/exceptions/SimpleCommandExceptionType;"
            )
    )
    private void command_crafter$addOpenListInternalRangeToTree(CallbackInfoReturnable<NbtElement> cir, @Share("listBuilder") LocalRef<NbtStringRangeListBuilder> listBuilderRef) {
        if(!command_crafter$allowMalformed) return;
        if(command_crafter$stringRangeTreeBuilder != null) {
            var whitespaceStartCursor = reader.getCursor() - 1;
            while(Character.isWhitespace(reader.getString().charAt(whitespaceStartCursor)))
                whitespaceStartCursor--;
            whitespaceStartCursor++;
            var listBuilder = listBuilderRef.get();
            if(listBuilder == null)
                listBuilder = command_crafter$currentParsingNbtListBuilder;
            listBuilder.addRangeBetweenEntries(new StringRange(whitespaceStartCursor, reader.getCursor()));
        }
    }


    @ModifyExpressionValue(
            method = "parseList",
            at = @At(
                    value = "NEW",
                    target = "net/minecraft/nbt/NbtList"
            )
    )
    private NbtList command_crafter$initNbtList(NbtList value, @Share("listBuilder") LocalRef<NbtStringRangeListBuilder> listBuilder) {
        if(command_crafter$allowMalformed && command_crafter$currentParsingNbtListBuilder != null) {
            // Return null, because entries are supposed to be added to the list builder instead of an NBT list
            value = null;
            listBuilder.set(command_crafter$currentParsingNbtListBuilder);
            command_crafter$currentParsingNbtListBuilder = null;
        }
        if(command_crafter$stringRangeTreeBuilder != null) {
            var listStartCursor = reader.getCursor() - 1;
            while(Character.isWhitespace(reader.getString().charAt(listStartCursor)))
                listStartCursor--;
            listStartCursor++;
            if(value != null) {
                command_crafter$stringRangeTreeBuilder.addNodeOrder(value);
                command_crafter$stringRangeTreeBuilder.addRangeBetweenInternalNodeEntries(value, new StringRange(listStartCursor, reader.getCursor()));
            } else {
                listBuilder.get().addRangeBetweenEntries(new StringRange(listStartCursor, reader.getCursor()));
            }
            command_crafter$elementAllowedStartCursor.push(listStartCursor);
        }
        return value;
    }

    @ModifyVariable(
            method = "parseList",
            at = @At("LOAD"),
            ordinal = 1
    )
    private NbtType<?> command_crafter$doNotCheckListTypeWhenAllowingMalformed(NbtType<?> value) {
        return command_crafter$allowMalformed ? null : value;
    }

    @WrapOperation(
            method = "parseList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtList;add(Ljava/lang/Object;)Z"
            )
    )
    private boolean command_crafter$addListEntryToListBuilder(NbtList instance, Object entry, Operation<Boolean> op, @Share("listBuilder") LocalRef<NbtStringRangeListBuilder> listBuilderRef) {
        final var listBuilder = listBuilderRef.get();
        if(listBuilder == null)
            return op.call(instance, entry);
        listBuilder.addElement((NbtElement)entry);
        return true;
    }

    @WrapOperation(
            method = "parseList",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;parseElement()Lnet/minecraft/nbt/NbtElement;"
            )
    )
    private NbtElement command_crafter$addErroringListEntry(StringNbtReader instance, Operation<NbtElement> original, @Local NbtList list, @Share("listBuilder") LocalRef<NbtStringRangeListBuilder> listBuilderRef) throws CommandSyntaxException {
        if (!command_crafter$allowMalformed) {
            return original.call(instance);
        }
        final var startChar = reader.getCursor();
        try {
            return MixinUtil.<NbtElement, CommandSyntaxException>callWithThrows(original, instance);
        } catch(CommandSyntaxException e) {
            final var listBuilder = listBuilderRef.get();
            final var entry = NbtEndAccessor.callInit();
            if (listBuilder != null) {
                listBuilder.addElement(entry);
                if(command_crafter$stringRangeTreeBuilder != null)
                    command_crafter$stringRangeTreeBuilder.addNode(entry, new StringRange(startChar, reader.getCursor()), command_crafter$elementAllowedStartCursor.peek());
            } else {
                list.add(entry);
            }
            throw e;
        }
    }

    @Inject(
            method = "parseElementPrimitiveArray",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
                    remap = false
            ),
            cancellable = true
    )
    private void command_crafter$allowMalformedPrimitiveArrayHeader(CallbackInfoReturnable<NbtElement> cir, @Local int contentStartCursor) throws CommandSyntaxException {
        if(!command_crafter$allowMalformed)
            return;
        // Parse as a list instead so contents of the array can have syntax errors
        command_crafter$skipFirstNestedChar = true;
        command_crafter$currentNestedStartChar = contentStartCursor - 1;
        cir.setReturnValue(parseList());
    }

    @WrapWithCondition(
            method = { "parseCompound", "parseList" },
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
