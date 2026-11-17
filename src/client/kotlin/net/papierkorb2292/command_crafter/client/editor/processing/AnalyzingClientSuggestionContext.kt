package net.papierkorb2292.command_crafter.client.editor.processing

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.ClientSuggestionContext
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.commands.suggestions.SuggestionContext.ElementSuggestionType
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.server.permissions.PermissionSetSupplier
import net.minecraft.world.entity.Entity
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.papierkorb2292.command_crafter.Util
import net.papierkorb2292.command_crafter.client.ClientCommandCrafter
import net.papierkorb2292.command_crafter.helper.getOrNull
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.stream.Stream

class AnalyzingClientSuggestionContext(
    private val clientCommandSource: ClientSuggestionContext,
    private val hasNetworkHandler: Boolean,
    private val registries: RegistryAccess,
) : SuggestionContext, PermissionSetSupplier {
    companion object {
        val CLIENT_SUGGESTION_CONTEXT_REGISTRY_OVERRIDE = ScopedValue.newInstance<RegistryAccess>()

        fun createConnectionlessClientSuggestionContext(minecraftClient: Minecraft, registries: RegistryAccess): ClientSuggestionContext =
            ScopedValue.where(CLIENT_SUGGESTION_CONTEXT_REGISTRY_OVERRIDE, registries).call<_, Nothing> {
                // Create a client suggestion context that uses the custom registries instead of getting them from the connection
                ClientSuggestionContext(Util.nullIsFine<ClientPacketListener>(null), minecraftClient, PermissionSet.ALL_PERMISSIONS)
            }
    }

    constructor(minecraftClient: Minecraft, registries: RegistryAccess): this(
        minecraftClient.connection?.suggestionsProvider as ClientSuggestionContext?
            ?: createConnectionlessClientSuggestionContext(minecraftClient, registries),
        minecraftClient.connection != null,
        registries,
    )

    override fun onlinePlayerNames(): Collection<String> =
        if(hasNetworkHandler) clientCommandSource.onlinePlayerNames() else Collections.emptyList()
    override fun allTeams(): Collection<String> =
        if(hasNetworkHandler) clientCommandSource.allTeams() else Collections.emptyList()

    override fun selectedEntities(): MutableCollection<String> = clientCommandSource.selectedEntities()
    override fun availableSounds(): Stream<Identifier> =
        clientCommandSource.availableSounds()
    override fun availablePostEffects(): Stream<Identifier> = clientCommandSource.availablePostEffects()
    override fun server(): MinecraftServer? = clientCommandSource.server()
    override fun level(): Level? = clientCommandSource.level()
    override fun levels(): MutableSet<ResourceKey<Level>> = clientCommandSource.levels()
    override fun entity(): Entity? = clientCommandSource.entity()
    override fun position(): Vec3 = clientCommandSource.position()

    // Also always contains advancements and registries (they're parsed by the client, and they're also synced with servers)
    override fun holderLookups(): HolderLookup.Provider = clientCommandSource.holderLookups()

    override fun suggestObjectiveNames(context: CommandContext<*>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> =
        clientCommandSource.suggestObjectiveNames(context, builder)

    override fun enabledFeatures(): FeatureFlagSet =
        if(hasNetworkHandler) clientCommandSource.enabledFeatures() else ClientCommandCrafter.defaultFeatureSet

    // Copied from ClientSuggestionProvider to use custom registries
    override fun <E : Any> suggestRegistryElements(
        context: CommandContext<*>,
        key: ResourceKey<out Registry<out E>>,
        elements: ElementSuggestionType,
        builder: SuggestionsBuilder,
        filter: Predicate<E>
    ): CompletableFuture<Suggestions> =
        holderLookups().lookup(key).map { registry ->
            suggestRegistryElements(registry, elements, builder, filter)
            builder.buildFuture()
        }.orElseGet { customSuggestion(context) }

    override fun customSuggestion(context: CommandContext<*>): CompletableFuture<Suggestions> {
        if(!hasNetworkHandler)
            return Suggestions.empty()
        val suggestionsGetter = VanillaLanguage.SERVERSIDE_SUGGESTION_GETTER.getOrNull()
        val result = if(suggestionsGetter == null) {
            // Use vanilla suggestions
            clientCommandSource.customSuggestion(context)
        } else {
            suggestionsGetter()
        }
        VanillaLanguage.SERVERSIDE_SUGGESTION_GETTER.set { Suggestions.empty() } // Only allow once per completion invocation to reduce unnecessary processing
        return result
    }

    override fun permissions(): PermissionSet = PermissionSet.ALL_PERMISSIONS
}