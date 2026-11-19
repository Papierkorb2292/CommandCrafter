package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.commands.suggestions.SuggestionContext.ElementSuggestionType
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Stream

class AnalyzingServerSuggestionContext(val server: MinecraftServer): SuggestionContext {
    private val filteredProvider: HolderLookup.Provider = server.reloadableRegistries().lookup()
            .filterFeatures(server.worldData.enabledFeatures())

    override fun permissions(): PermissionSet = server.functionCompilationPermissions
    override fun position(): Vec3 = Vec3.ZERO
    override fun level(): ServerLevel = server.overworld()
    override fun enabledFeatures(): FeatureFlagSet = level().enabledFeatures()
    override fun entity(): Entity? = null
    override fun server(): MinecraftServer = server
    override fun onlinePlayerNames(): Collection<String> = server.playerNames.toList()
    override fun allTeams(): Collection<String> = server.scoreboard.teamNames
    override fun availableSounds(): Stream<Identifier> =
        BuiltInRegistries.SOUND_EVENT.stream().map(SoundEvent::location)
    override fun availablePostEffects(): Stream<Identifier> = Stream.empty()
    override fun suggestObjectiveNames(context: CommandContext<*>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        return SuggestionContext.suggest(this.server.scoreboard.objectiveNames, builder)
    }
    override fun levels(): MutableSet<ResourceKey<Level>> = server.levelKeys()
    override fun holderLookups(): HolderLookup.Provider = this.filteredProvider

    override fun <E : Any> suggestRegistryElements(
        context: CommandContext<*>,
        key: ResourceKey<out Registry<out E>>,
        elements: ElementSuggestionType,
        builder: SuggestionsBuilder,
        filter: Predicate<E>,
    ): CompletableFuture<Suggestions> =
        holderLookups().lookup(key).map { registry ->
            suggestRegistryElements(registry, elements, builder, filter)
            builder.buildFuture()
        }.orElseGet(Supplier { Suggestions.empty() })

    override fun customSuggestion(context: CommandContext<*>): CompletableFuture<Suggestions> {
        return Suggestions.empty()
    }
}