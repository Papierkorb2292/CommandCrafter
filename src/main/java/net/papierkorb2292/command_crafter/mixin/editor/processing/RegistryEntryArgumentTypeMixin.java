package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtEnd;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
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

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var readerCopy = reader.copy();
        readerCopy.setCursor(range.getStart());
        try {
            var id = Identifier.fromCommandInputNonEmpty(readerCopy);
            if (command_crafter$packContentFileType != null) {
                IdArgumentTypeAnalyzer.INSTANCE.analyzeForId(id, command_crafter$packContentFileType, range, result, reader);
                return;
            }
            result.getSemanticTokens().addMultiline(range, TokenType.Companion.getPARAMETER(), 0);
            return;
        } catch(CommandSyntaxException ignored) { }
        readerCopy.setCursor(range.getStart());
        var nbtReader = new StringNbtReader(readerCopy);
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        NbtElement nbt;
        try {
            nbt = nbtReader.parseElement();
        } catch(CommandSyntaxException e) {
            nbt = NbtEnd.INSTANCE;
            treeBuilder.addNode(nbt, range, range.getStart());
        }
        var tree = treeBuilder.build(nbt);
        tree.generateSemanticTokens(new NbtSemanticTokenProvider(tree, readerCopy.getString()), result.getSemanticTokens());
        final var languageServer = reader.getResourceCreator().getLanguageServer();
        if(languageServer != null) {
            final var decodingResult = StringRangeTree.AnalyzingDynamicOps.Companion.decodeWithAnalyzingOps(RegistryOps.of(NbtOps.INSTANCE, registries), tree, entryCodec);
            // Copy the input to ignore changes to the mapping info
            final var nbtAnalyzingResult = result.copyInput();
            decodingResult.getFirst().suggestFromAnalyzingOps(
                    decodingResult.getSecond(),
                    nbtAnalyzingResult,
                    reader.getResourceCreator().getLanguageServer(),
                    new NbtSuggestionResolver(reader)
            );
            result.combineWith(nbtAnalyzingResult);
        }
    }

    @Override
    public boolean command_crafter$hasCustomCompletions(@NotNull CommandContext<CommandSource> context, @NotNull String name) {
        return true;
    }
}