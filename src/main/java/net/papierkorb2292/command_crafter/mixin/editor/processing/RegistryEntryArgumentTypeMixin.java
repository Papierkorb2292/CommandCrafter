package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.component.ComponentType;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.*;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(RegistryEntryArgumentType.class)
public class RegistryEntryArgumentTypeMixin<T> implements AnalyzingCommandNode, CustomCompletionsCommandNode {

    @Shadow @Final private Codec<RegistryEntry<T>> entryCodec;
    @Shadow @Final private RegistryWrapper.WrapperLookup registries;
    private PackContentFileType command_crafter$packContentFileType = null;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void command_crafter$selectPackContentFileType(CommandRegistryAccess registryAccess, RegistryKey<?> registry, Codec<?> entryCodec, CallbackInfo ci) {
        if(registry == RegistryKeys.PREDICATE)
            command_crafter$packContentFileType = PackContentFileType.PREDICATES_FILE_TYPE;
        else if(registry == RegistryKeys.ITEM_MODIFIER)
            command_crafter$packContentFileType = PackContentFileType.ITEM_MODIFIER_FILE_TYPE;
        else if(registry == RegistryKeys.LOOT_TABLE)
            command_crafter$packContentFileType = PackContentFileType.LOOT_TABLES_FILE_TYPE;
    }

    @ModifyReceiver(
            method = "parse(Lcom/mojang/brigadier/StringReader;Lnet/minecraft/nbt/StringNbtReader;)Lnet/minecraft/registry/entry/RegistryEntry;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private DataResult<RegistryEntry<T>> command_crafter$suppressDecoderErrorsWhenAnalyzing(DataResult<RegistryEntry<T>> original, Function<String, ?> stringEFunction, StringReader reader, @Cancellable CallbackInfoReturnable<RegistryEntry<T>> cir) {
        // Skip components with errors when analyzing, because decoder diagnostics are already generated through command_crafter$analyze
        // This also makes the analyzer more forgiving
        if(original.isError() && reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            cir.setReturnValue(null);
        }
        return original;
    }

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var readerCopy = reader.copy();
        readerCopy.setCursor(range.getStart());
        Identifier parsedId = null;
        try {
            parsedId = Identifier.fromCommandInputNonEmpty(readerCopy);
            if (command_crafter$packContentFileType != null) {
                IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(parsedId, command_crafter$packContentFileType, range, result, reader);
            } else {
                result.getSemanticTokens().addMultiline(range, TokenType.Companion.getPARAMETER(), 0);
            }
        } catch(CommandSyntaxException ignored) { }
        readerCopy.setCursor(range.getStart());
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        NbtElement nbt;
        if(parsedId == null) {
            var nbtReader = StringNbtReader.fromOps(NbtOps.INSTANCE);
            ((AllowMalformedContainer) nbtReader).command_crafter$setAllowMalformed(true);
            //noinspection unchecked
            ((StringRangeTreeCreator<NbtElement>) nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
            try {
                nbt = nbtReader.readAsArgument(readerCopy);
            } catch (CommandSyntaxException e) {
                nbt = NbtEnd.INSTANCE;
                treeBuilder.addNode(nbt, range, range.getStart());
            }
        } else {
            nbt = NbtString.of(parsedId.toString());
            treeBuilder.addNode(nbt, range, range.getStart());
        }
        var tree = treeBuilder.build(nbt);
        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                readerCopy
        )
                .withSuggestionResolver(new NbtSuggestionResolver(readerCopy, nbtString -> Identifier.tryParse(nbtString.value()) == null))
                .withRegistry(registries)
                .analyzeFull(result, parsedId == null, entryCodec);
    }

    @Override
    public boolean command_crafter$hasCustomCompletions(@NotNull CommandContext<CommandSource> context, @NotNull String name) {
        return true;
    }
}