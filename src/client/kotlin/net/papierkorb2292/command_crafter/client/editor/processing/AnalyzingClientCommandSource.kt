package net.papierkorb2292.command_crafter.client.editor.processing

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.server.permissions.PermissionSetSupplier
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.level.Level
import net.papierkorb2292.command_crafter.Util
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.processing.helper.CompletionItemsContainer
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream

class AnalyzingClientCommandSource(
    private val clientCommandSource: ClientSuggestionProvider,
    private val hasNetworkHandler: Boolean,
    private val languageServer: MinecraftLanguageServer,
) : SharedSuggestionProvider, PermissionSetSupplier {

    companion object {
        // This is saved globally instead of per instance, because ClientCommandCrafter can only
        // access the latest instance, but macros might be using a previous instance
        val allowServersideCompletions = ThreadLocal<Boolean>()
    }

    constructor(minecraftClient: Minecraft, languageServer: MinecraftLanguageServer): this(
        minecraftClient.connection?.suggestionsProvider
            ?: ClientSuggestionProvider(Util.nullIsFine<ClientPacketListener>(null), minecraftClient, PermissionSet.ALL_PERMISSIONS),
        minecraftClient.connection != null,
        languageServer
    )

    override fun getOnlinePlayerNames(): Collection<String> =
        if(hasNetworkHandler) clientCommandSource.onlinePlayerNames else Collections.emptyList()
    override fun getAllTeams(): Collection<String> =
        if(hasNetworkHandler) clientCommandSource.allTeams else Collections.emptyList()

    override fun getSelectedEntities(): MutableCollection<String> = clientCommandSource.selectedEntities
    override fun getAvailableSounds(): Stream<Identifier> =
        clientCommandSource.availableSounds
    override fun levels(): MutableSet<ResourceKey<Level>> = clientCommandSource.levels()
    // Note: There are some 'registries' parsed by LoadedClientsideRegistries that are not synced
    // when connecting to a server. Advancements and recipes will be in registryAccess() only if the player is not connected
    // to a server with CommandCrafter.
    override fun registryAccess(): RegistryAccess = languageServer.dynamicRegistryManager
    override fun enabledFeatures(): FeatureFlagSet =
        if(hasNetworkHandler) clientCommandSource.enabledFeatures() else ClientCommandCrafter.defaultFeatureSet

    // Copied from ClientSuggestionProvider to use custom registries
    override fun suggestRegistryElements(
        registryRef: ResourceKey<out Registry<*>>,
        suggestedIdType: SharedSuggestionProvider.ElementSuggestionType,
        builder: SuggestionsBuilder,
        context: CommandContext<*>,
    ): CompletableFuture<Suggestions> =
        registryAccess().lookup(registryRef).map { registry ->
            suggestRegistryElements(registry, suggestedIdType, builder)
            builder.buildFuture()
        }.orElseGet { customSuggestion(context) }

    override fun customSuggestion(context: CommandContext<*>): CompletableFuture<Suggestions> {
        if(allowServersideCompletions.getOrNull() != true)
            return Suggestions.empty()
        allowServersideCompletions.remove() // Only allow once per completion invocation to reduce unnecessary processing
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
            return clientCommandSource.customSuggestion(context)
        return Suggestions.empty()
    }

    override fun permissions(): PermissionSet = PermissionSet.ALL_PERMISSIONS
}