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
import com.mojang.serialization.DataResult;
import net.minecraft.command.argument.ItemStringReader;
import net.minecraft.component.ComponentType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryOps;
import net.papierkorb2292.command_crafter.MixinUtil;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResultDataContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(targets = "net.minecraft.command.argument.ItemStringReader$Reader")
public class ItemStringReaderReaderMixin {

    @Final @Shadow
    ItemStringReader field_48970;

    @Shadow @Final private StringReader reader;
    private final AnalyzingResult command_crafter$analyzingResult = ((AnalyzingResultDataContainer)field_48970).command_crafter$getAnalyzingResult();

    @Inject(
            method = "readItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Identifier;fromCommandInput(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/util/Identifier;",
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
                    target = "Lnet/minecraft/command/argument/ItemStringReader$Reader;readComponentType(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/component/ComponentType;"
            )
    )
    private void command_crafter$saveComponentTypeStart(CallbackInfo ci, @Share("componentTypeStart") LocalIntRef startCursor) {
        startCursor.set(reader.getCursor());
    }

    @Inject(
            method = "readComponents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/command/argument/ItemStringReader$Reader;readComponentType(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/component/ComponentType;",
                    shift = At.Shift.AFTER
            )
    )
    private void command_crafter$highlightComponentId(CallbackInfo ci, @Share("componentTypeStart") LocalIntRef startCursor) {
        if(command_crafter$analyzingResult != null) {
            command_crafter$analyzingResult.getSemanticTokens().addMultiline(startCursor.get(), reader.getCursor() - startCursor.get(), TokenType.Companion.getPARAMETER(), 0);
        }
    }

    @WrapOperation(
            method = "readComponentValue",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/StringNbtReader;readAsArgument(Lcom/mojang/brigadier/StringReader;)Ljava/lang/Object;"
            )
    )
    private <O, T> O command_crafter$analyzeComponentNbt(StringNbtReader<O> instance, StringReader reader, Operation<O> op, StringNbtReader<O> originalNbtReader,  RegistryOps<O> ops, ComponentType<T> type) {
        if (command_crafter$analyzingResult == null || type.getCodec() == null || !(ops.empty() instanceof NbtElement)) {
            return op.call(instance, reader);
        }
        //noinspection unchecked
        final var directiveReader = (DirectiveStringReader<AnalyzingResourceCreator>)reader;
        final var nbtReader = StringNbtReader.fromOps(NbtOps.INSTANCE);
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        final var startCursor = reader.getCursor();
        NbtElement nbt;
        try {
            nbt = (NbtElement)MixinUtil.<O, CommandSyntaxException>callWithThrows(op, nbtReader, reader);
        } catch(CommandSyntaxException e) {
            nbt = NbtEnd.INSTANCE;
            treeBuilder.addNode(nbt, new StringRange(startCursor, reader.getCursor()), startCursor);
        }
        var tree = treeBuilder.build(nbt);
        var treeOps = StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                directiveReader
        ).withOps(((ItemStringReaderAccessor)field_48970).getOps());
        treeOps.analyzeFull(command_crafter$analyzingResult, true, type.getCodec());
        return (O)nbt;
    }

    @ModifyReceiver(
            method = "readComponentValue",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private <T> DataResult<T> command_crafter$suppressDecoderErrorsWhenAnalyzing(DataResult<T> original, Function<String, ?> stringEFunction, @Cancellable CallbackInfo ci) {
        // Skip components with errors when analyzing, because decoder diagnostics are already generated through command_crafter$analyzeComponentNbt
        // This also makes the analyzer more forgiving
        if(original.isError() && reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            ci.cancel();
        }
        return original;
    }

    @WrapMethod(
            method = "readComponents"
    )
    private void command_crafter$allowMalformedComponents(Operation<Void> op) throws CommandSyntaxException {
        if(command_crafter$analyzingResult == null) {
            op.call();
            return;
        }
        // This is usually done by the readComponents method, but it's disabled when generating
        // an analyzing result, because it would interfere with the recursion
        reader.expect('[');
        command_crafter$parseMalformedComponents(op);
    }

    private void command_crafter$parseMalformedComponents(Operation<Void> op) {
        try {
            MixinUtil.<Void, CommandSyntaxException>callWithThrows(op);
        } catch (CommandSyntaxException e) {
            // Skip to next property
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
        return command_crafter$analyzingResult == null;
    }
}
