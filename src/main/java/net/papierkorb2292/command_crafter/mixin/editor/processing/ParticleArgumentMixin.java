package net.papierkorb2292.command_crafter.mixin.editor.processing;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(ParticleArgument.class)
public class ParticleArgumentMixin implements AnalyzingCommandNode {
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Override
    public void command_crafter$analyze(@NotNull CommandContext<@NotNull SharedSuggestionProvider> context, @NotNull StringRange range, @NotNull DirectiveStringReader<@NotNull AnalyzingResourceCreator> reader, @NotNull AnalyzingResult result, @NotNull String name) throws CommandSyntaxException {
        Decoder<?> parameterDecoder = null;

        try {
            final var startPos = reader.getCursor();
            final var particleId = Identifier.read(reader);
            result.getSemanticTokens().addMultiline(startPos, reader.getCursor() - startPos, TokenType.Companion.getPARAMETER(), 0);
            final var registry = this.registries.lookup(Registries.PARTICLE_TYPE);
            if(registry.isPresent()) {
                final var particleType = registry.get().get(ResourceKey.create(Registries.PARTICLE_TYPE, particleId));
                if(particleType.isPresent() && !(particleType.get().value() instanceof SimpleParticleType)) {
                    parameterDecoder = particleType.get().value().codec().decoder();
                }
            }
        } catch (CommandSyntaxException ignored) {}

        var optionsReader = reader;
        final var hasNbt = reader.canRead() && reader.peek() == '{';
        if(!hasNbt) {
            // Don't read too much, since there might still be other arguments there and analyzer shouldn't skip whitespace that's not part of the node (important for macros)
            // But still try to read in NBT for suggestions and error checking
            optionsReader = optionsReader.copy();
            optionsReader.toCompleted();
            optionsReader.setString(reader.getString().substring(0, reader.getCursor()));
        }

        var nbtReader = TagParser.create(NbtOps.INSTANCE);
        var treeBuilder = new StringRangeTree.Builder<Tag>();
        ((AllowMalformedContainer)nbtReader).command_crafter$setAllowMalformed(true);
        //noinspection unchecked
        ((StringRangeTreeCreator<Tag>)nbtReader).command_crafter$setStringRangeTreeBuilder(treeBuilder);
        var nbt = nbtReader.parseAsArgument(optionsReader);
        var tree = treeBuilder.build(nbt);

        StringRangeTree.TreeOperations.Companion.forNbt(tree, reader)
            .withDiagnosticSeverity(DiagnosticSeverity.Error)
            .analyzeFull(result, hasNbt, parameterDecoder);
    }

    @ModifyReceiver(
            method = "readParticle(Lnet/minecraft/nbt/TagParser;Lcom/mojang/brigadier/StringReader;Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/core/particles/ParticleOptions;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/DataResult;getOrThrow(Ljava/util/function/Function;)Ljava/lang/Object;"
            )
    )
    private static <T> DataResult<T> command_crafter$suppressDecoderErrorsWhenAnalyzing(DataResult<T> original, Function<String, ?> stringEFunction, @Cancellable CallbackInfoReturnable<Object> ci, @Local(argsOnly = true) StringReader reader) {
        if(original.isError() && reader instanceof DirectiveStringReader<?> directiveStringReader && directiveStringReader.getResourceCreator() instanceof AnalyzingResourceCreator) {
            ci.setReturnValue(null);
        }
        return original;
    }
}
