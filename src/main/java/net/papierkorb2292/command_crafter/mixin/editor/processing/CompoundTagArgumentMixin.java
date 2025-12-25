package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Decoder;
import kotlin.jvm.functions.Function1;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.commands.CommandSourceStack;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.DataObjectDecoding;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.helper.*;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CompoundTagArgument.class)
public class CompoundTagArgumentMixin implements AnalyzingCommandNode, DataObjectSourceContainer {

    private DataObjectDecoding.DataObjectSource command_crafter$dataObjectSource;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        var nbtReader = TagParser.create(NbtOps.INSTANCE);
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        //noinspection unchecked
        ((StringRangeTreeCreator<Tag>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        var nbt = nbtReader.parseAsArgument(reader);
        var tree = treeBuilder.build(nbt);

        Decoder<?> decoder = null;
        if(command_crafter$dataObjectSource != null) {
            var dataObjectDecoding = DataObjectDecoding.Companion.getForReader(reader);
            if(dataObjectDecoding != null)
                decoder = dataObjectDecoding.getDecoderForSource(command_crafter$dataObjectSource, context);
        }

        StringRangeTree.TreeOperations.Companion.forNbt(
                tree,
                reader
        )
                .withDiagnosticSeverity(DiagnosticSeverity.Warning)
                .analyzeFull(result, true, decoder);
    }

    @Override
    public void command_crafter$setDataObjectSource(@NotNull DataObjectDecoding.DataObjectSource dataObjectSource) {
        command_crafter$dataObjectSource = dataObjectSource;
    }

    @Override
    public @Nullable DataObjectDecoding.DataObjectSource command_crafter$getDataObjectSource() {
        return command_crafter$dataObjectSource;
    }
}
