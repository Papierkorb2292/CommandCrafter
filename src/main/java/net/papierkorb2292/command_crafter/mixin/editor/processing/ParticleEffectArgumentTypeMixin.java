package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Decoder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ParticleEffectArgumentType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator;
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTree;
import net.papierkorb2292.command_crafter.editor.processing.TokenType;
import net.papierkorb2292.command_crafter.editor.processing.helper.AllowMalformedContainer;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingCommandNode;
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult;
import net.papierkorb2292.command_crafter.editor.processing.helper.StringRangeTreeCreator;
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ParticleEffectArgumentType.class)
public class ParticleEffectArgumentTypeMixin implements AnalyzingCommandNode {
    @Shadow
    @Final
    private RegistryWrapper.WrapperLookup registries;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<@NotNull CommandSource> context, @NotNull StringRange range, @NotNull DirectiveStringReader<@NotNull AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        Decoder<?> parameterDecoder = null;

        try {
            final var startPos = reader.getCursor();
            final var particleId = Identifier.fromCommandInput(reader);
            result.getSemanticTokens().addMultiline(startPos, reader.getCursor() - startPos, TokenType.Companion.getPARAMETER(), 0);
            final var registry = this.registries.getOptional(RegistryKeys.PARTICLE_TYPE);
            if(registry.isPresent()) {
                final var particleType = registry.get().getOptional(RegistryKey.of(RegistryKeys.PARTICLE_TYPE, particleId));
                if(particleType.isPresent() && !(particleType.get().value() instanceof SimpleParticleType)) {
                    parameterDecoder = particleType.get().value().getCodec().decoder();
                }
            }
        } catch (CommandSyntaxException ignored) {}

        final var hasNbt = reader.canRead() && reader.peek() == '{';

        var nbtReader = StringNbtReader.fromOps(NbtOps.INSTANCE);
        var treeBuilder = new StringRangeTree.Builder<NbtElement>();
        // Clamped tree ranges if there's no nbt, because otherwise the tree might contain the whitespace after the argument and completions would replace it
        if(!hasNbt) {
            treeBuilder.setClampNodeRange(StringRange.at(reader.getCursor()));
        }
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        //noinspection unchecked
        ((StringRangeTreeCreator<NbtElement>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        var nbt = nbtReader.readAsArgument(reader);
        var tree = treeBuilder.build(nbt);

        StringRangeTree.TreeOperations.Companion.forNbt(tree, reader)
            .withDiagnosticSeverity(DiagnosticSeverity.Error)
            .analyzeFull(result, hasNbt, parameterDecoder);
    }
}
