package net.papierkorb2292.command_crafter.client.editor.processing

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientCommandSource
import net.minecraft.command.CommandSource
import net.minecraft.command.PermissionLevelSource
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.util.Identifier
import net.minecraft.world.World
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class AnalyzingClientCommandSource(
    private val clientCommandSource: ClientCommandSource,
    private val hasNetworkHandler: Boolean
) : CommandSource, PermissionLevelSource {

    constructor(minecraftClient: MinecraftClient): this(
        minecraftClient.networkHandler?.commandSource ?: ClientCommandSource(null, minecraftClient, true),
        minecraftClient.networkHandler != null
    )

    override fun getPlayerNames(): Collection<String> =
        if(hasNetworkHandler) clientCommandSource.playerNames else Collections.emptyList()
    override fun getTeamNames(): Collection<String> =
        if(hasNetworkHandler) clientCommandSource.teamNames else Collections.emptyList()

    override fun getEntitySuggestions(): MutableCollection<String> = clientCommandSource.entitySuggestions
    override fun getSoundIds(): Stream<Identifier> =
        clientCommandSource.soundIds
    override fun getWorldKeys(): MutableSet<RegistryKey<World>> = clientCommandSource.worldKeys
    override fun getRegistryManager(): DynamicRegistryManager =
        if(hasNetworkHandler) clientCommandSource.registryManager else DynamicRegistryManager.of(Registries.REGISTRIES)
    override fun getEnabledFeatures(): FeatureSet =
        if(hasNetworkHandler) clientCommandSource.enabledFeatures else ClientCommandCrafter.defaultFeatureSet
    override fun hasPermissionLevel(level: Int) = true

    override fun listIdSuggestions(
        registryRef: RegistryKey<out Registry<*>>,
        suggestedIdType: CommandSource.SuggestedIdType,
        builder: SuggestionsBuilder,
        context: CommandContext<*>,
    ): CompletableFuture<Suggestions> =
        if(registryManager.getOptional(registryRef).isPresent)
            clientCommandSource.listIdSuggestions(registryRef, suggestedIdType, builder, context)
        else
            getCompletions(context)

    override fun getCompletions(context: CommandContext<*>): CompletableFuture<Suggestions> {
        val fullInput = VanillaLanguage.SUGGESTIONS_FULL_INPUT.getOrNull()
        if(!hasNetworkHandler || fullInput == null)
            return Suggestions.empty()

        val contextCompletionProvider = fullInput.resourceCreator.languageServer?.minecraftServer?.contextCompletionProvider
        if(contextCompletionProvider != null)
            return contextCompletionProvider.getCompletions(fullInput).thenApply {
                Suggestions(StringRange.at(0), emptyList()).apply {
                    @Suppress("KotlinConstantConditions")
                    (this as CompletionItemsContainer).`command_crafter$setCompletionItem`(it)
                }
            }
        if(!VanillaLanguage.isReaderEasyNextLine(fullInput) && !VanillaLanguage.isReaderInlineResources(fullInput))
            return clientCommandSource.getCompletions(context)
        return Suggestions.empty()
    }
}