package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryElementCodec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.packrat.PackratParser;
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

@Mixin(RegistryEntryArgumentType.class)
public class RegistryEntryArgumentTypeMixin<T> implements AnalyzingCommandNode, CustomCompletionsCommandNode {

    @Shadow @Final private Codec<RegistryEntry<T>> entryCodec;
    @Shadow @Final private RegistryWrapper.WrapperLookup registries;
    @Shadow @Final private PackratParser<RegistryEntryArgumentType.EntryParser<T, NbtElement>> parser;
    @Shadow @Final private RegistryKey<? extends Registry<T>> registryRef;
    private PackContentFileType command_crafter$packContentFileType = null;

    private Codec<?> command_crafter$inlineOrReferenceCodec = null;

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
        else if(registry == RegistryKeys.DIALOG)
            command_crafter$packContentFileType = PackContentFileType.DIALOG_FILE_TYPE;
    }

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        var partialBuilder = new StringRangeTree.PartialBuilder<NbtElement>();

        RegistryEntryArgumentType.EntryParser<T, NbtElement> parsed;

        try {
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().set(new PackratParserAdditionalArgs.StringRangeTreeBranchingArgument<>(partialBuilder));
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().set(true);
            parsed = parser.parse(reader);
        } catch(CommandSyntaxException e) {
            parsed = new RegistryEntryArgumentType.DirectParser<>(NbtEnd.INSTANCE);
            var node = partialBuilder.pushNode();
            node.setNode(NbtEnd.INSTANCE);
            node.setNodeAllowedStart(range.getStart());
            node.setStartCursor(range.getStart());
            node.setEndCursor(range.getEnd());
        } finally {
            PackratParserAdditionalArgs.INSTANCE.getNbtStringRangeTreeBuilder().remove();
            PackratParserAdditionalArgs.INSTANCE.getAllowMalformed().remove();
        }

        NbtElement treeRoot;

        switch(parsed) {
            case RegistryEntryArgumentType.ReferenceParser<T, NbtElement>(RegistryKey<T> key) -> {
                if(command_crafter$packContentFileType != null)
                    IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(key.getValue(), command_crafter$packContentFileType, range, result, reader);
                else
                    result.getSemanticTokens().addMultiline(range, TokenType.Companion.getPARAMETER(), 0);

                treeRoot = NbtString.of(key.getValue().toString());
                treeBuilder.addNode(treeRoot, range, range.getStart());
            }
            case RegistryEntryArgumentType.DirectParser<T, NbtElement>(NbtElement value) -> {
                treeRoot = value;
                partialBuilder.addToBasicBuilder(treeBuilder);
            }
        }

        var isInline = parsed instanceof RegistryEntryArgumentType.DirectParser<T, NbtElement>;

        if(command_crafter$inlineOrReferenceCodec == null)
            command_crafter$inlineOrReferenceCodec = RegistryElementCodec.of(registryRef, entryCodec.xmap(RegistryEntry::value, RegistryEntry::of));

        var tree = treeBuilder.build(treeRoot);
        var treeOperations = StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                reader
        )
                .withSuggestionResolver(new NbtSuggestionResolver(reader, nbtString -> Identifier.tryParse(nbtString.value()) == null))
                .withRegistry(registries);
        if(!isInline)
            treeOperations = treeOperations.withDiagnosticSeverity(null);
        treeOperations.analyzeFull(result, isInline, command_crafter$inlineOrReferenceCodec);
    }

    @Override
    public boolean command_crafter$hasCustomCompletions(@NotNull CommandContext<CommandSource> context, @NotNull String name) {
        return true;
    }
}