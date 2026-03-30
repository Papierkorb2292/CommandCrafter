package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import kotlin.Unit;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.TreeOperations;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Mixin(targets = "net.minecraft.commands.arguments.item.ItemParser$State")
public class ItemStringReaderReaderMixin {

    @Shadow @Final private StringReader reader;
    @Shadow
    @Final
    ItemParser this$0;

    private final AnalyzingResult command_crafter$analyzingResult = ((AnalyzingResultDataContainer) this$0).command_crafter$getAnalyzingResult();
    private boolean command_crafter$allowMalformed = ((AllowMalformedContainer) this$0).command_crafter$getAllowMalformed();
    private int command_crafter$suggestionStartCursor = -1;

    @Inject(
            method = "readItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resources/Identifier;read(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/resources/Identifier;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$highlightItemId(CallbackInfo ci, @Local int startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor, reader.getCursor() - startCursor, TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @Inject(
            method = "readComponents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/item/ItemParser$State;readComponentType(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/core/component/DataComponentType;"
            )
    )
    private void command_crafter$saveComponentTypeStart(CallbackInfo ci, @Share("componentTypeStart") LocalIntRef startCursor) {
        startCursor.set(reader.getCursor());
    }

    @Inject(
            method = "readComponents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/item/ItemParser$State;readComponentType(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/core/component/DataComponentType;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$highlightComponentId(CallbackInfo ci, @Share("componentTypeStart") LocalIntRef startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor.get(), reader.getCursor() - startCursor.get(), TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @WrapOperation(
            method = "readComponent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/TagParser;parseAsArgument(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private <O, T> O command_crafter$analyzeComponentNbt(TagParser<O> instance, StringReader reader, Operation<O> op, TagParser<O> originalNbtReader, RegistryOps<O> ops, DataComponentType<T> type) {
        if(!command_crafter$allowMalformed) {
            return op.call(instance, reader);
        }
        final var nbtReader = TagParser.create(NbtOps.INSTANCE);
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        //noinspection unchecked
        ((StringRangeTreeCreator<Tag>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(command_crafter$allowMalformed);
        ((AnalyzingResultCreator)nbtReader).command_crafter$setAnalyzingResult(command_crafter$analyzingResult);
        final var startCursor = reader.getCursor();
        Tag nbt;
        try {
            nbt = (Tag)MixinUtil.<O, CommandSyntaxException>callWithThrows(op, nbtReader, reader);
        } catch(CommandSyntaxException e) {
            nbt = EndTag.INSTANCE;
            treeBuilder.addNode(nbt, new StringRange(startCursor, reader.getCursor()), startCursor);
        }
        var tree = treeBuilder.build(nbt);
        if(command_crafter$analyzingResult != null) {
            //noinspection unchecked
            final var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)reader;
            TreeOperations.Companion.forNbt(
                    tree,
                    directiveReader
            ).withRegistry(directiveReader.getResourceCreator().getRegistries())
                    .analyzeFull(command_crafter$analyzingResult, type.codec());
        } else if(!reader.canRead()) {
            // Check if the nbt was ended correctly (otherwise don't give other suggestions)
            if(nbt instanceof EndTag)
                command_crafter$suggestionStartCursor = reader.getCursor();
            else if(nbt instanceof CompoundTag || nbt instanceof CollectionTag) {
                if(nbt instanceof CompoundTag && reader.peek(-1) != '}') {
                    command_crafter$suggestionStartCursor = reader.getCursor();
                } else if(nbt instanceof CollectionTag && reader.peek(-1) != ']') {
                    command_crafter$suggestionStartCursor = reader.getCursor();
                } else if(tree.getRanges().values().stream().filter(range -> range.getEnd() == reader.getCursor()).count() > 1){
                    // A child compound/list ended here
                    command_crafter$suggestionStartCursor = reader.getCursor();
                }
            }
        }
        return (O)nbt;
    }

    @ModifyReceiver(
            method = "readComponent",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private <T> DataResult<T> command_crafter$suppressDecoderErrorsForMalformed(DataResult<T> original, Function<String, ?> stringEFunction, @Cancellable CallbackInfo ci) {
        if(original.isError() && (reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator || command_crafter$allowMalformed)) {
            ci.cancel();
        }
        return original;
    }

    @WrapMethod(
            method = "readItem"
    )
    private void command_crafter$allowMalformedItem(Operation<Void> op) throws CommandSyntaxException {
        if(!command_crafter$allowMalformed) {
            op.call();
            return;
        }

        try {
            MixinUtil.<Void, CommandSyntaxException>callWithThrows(op);
        } catch (CommandSyntaxException e) {
            command_crafter$suggestionStartCursor = reader.getCursor();
            // Skip to components
            while(reader.canRead() && reader.peek() != '[' && reader.peek() != ' ') {
                reader.skip();
            }
            if(reader.canRead())
                command_crafter$suggestionStartCursor = -1;
        }
    }

    @WrapOperation(
            method = "readComponents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/item/ItemParser$State;readComponentType(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/core/component/DataComponentType;"
            )
    )
    private DataComponentType<?> command_crafter$allowMalformedComponentName(StringReader reader, Operation<DataComponentType<?>> op) throws CommandSyntaxException {
        if(!command_crafter$allowMalformed) {
            return op.call(reader);
        }

        try {
            return MixinUtil.<DataComponentType<?>, CommandSyntaxException>callWithThrows(op, reader);
        } catch (CommandSyntaxException e) {
            command_crafter$suggestionStartCursor = reader.getCursor();
            // Skip to components
            while(reader.canRead() && reader.peek() != '=' && reader.peek() != ',' && reader.peek() != ']' && reader.peek() != ' ') {
                reader.skip();
            }
            if(reader.canRead())
                command_crafter$suggestionStartCursor = -1;
        }
        return DataComponentType.<Unit>builder().persistent(MapCodec.unit(Unit.INSTANCE).codec()).build();
    }

    @WrapWithCondition(
            method = {"parse", "readComponents" },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/arguments/item/ItemParser$Visitor;visitSuggestions(Ljava/util/function/Function;)V"
            )
    )
    private boolean command_crafter$suggestComponentsForMalformedComponentNames(ItemParser.Visitor instance, Function<SuggestionsBuilder, CompletableFuture<Suggestions>> suggestor) {
        return !command_crafter$allowMalformed || command_crafter$suggestionStartCursor == -1;
    }

    @WrapMethod(
            method = "readComponents"
    )
    private void command_crafter$allowMalformedComponents(Operation<Void> op) throws CommandSyntaxException {
        if(!command_crafter$allowMalformed) {
            op.call();
            return;
        }
        // This is usually done by the readComponents method, but it's disabled when generating
        // allowing malformed, because it would interfere with the recursion
        reader.expect('[');
        command_crafter$parseMalformedComponents(op);
    }

    private void command_crafter$parseMalformedComponents(Operation<Void> op) {
        try {
            MixinUtil.<Void, CommandSyntaxException>callWithThrows(op);
        } catch (CommandSyntaxException e) {
            // Skip to next property
            // Don't set suggestion start cursor here, because it could override the start cursor from the component name
            while(reader.canRead() && reader.peek() != ',' && reader.peek() != ']')
                reader.skip();
            if(!reader.canRead())
                return;

            if(reader.peek() == ',')
                reader.skip();
            // Invoke the parser again
            command_crafter$parseMalformedComponents(op);
        }
    }

    @WrapWithCondition(
            method = "readComponents",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/brigadier/StringReader;expect(C)V",
                    ordinal = 0,
                    remap = false
            )
    )
    private boolean command_crafter$removeStartingBracketsForMalformedComponents(StringReader instance, char c) {
        return !command_crafter$allowMalformed;
    }

    @ModifyVariable(
            method = {
                    "suggestItem",
                    "suggestComponentAssignmentOrRemoval(Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;)Ljava/util/concurrent/CompletableFuture;",
                    "suggestComponent(Lcom/mojang/brigadier/suggestion/SuggestionsBuilder;Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;"
            },
            at = @At("HEAD"),
            argsOnly = true
    )
    private SuggestionsBuilder command_crafter$adjustSuggestionStartCursorForMalformedInput(SuggestionsBuilder original) {
        if(command_crafter$allowMalformed && command_crafter$suggestionStartCursor != -1) {
            return original.createOffset(command_crafter$suggestionStartCursor);
        }
        return original;
    }
}
