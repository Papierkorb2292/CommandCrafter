package net.papierkorb2292.command_crafter.helper

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.component.type.MapIdComponent
import net.minecraft.entity.Entity
import net.minecraft.entity.boss.dragon.EnderDragonPart
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.Fluid
import net.minecraft.item.FuelRegistry
import net.minecraft.item.map.MapState
import net.minecraft.particle.BlockParticleEffect
import net.minecraft.particle.ParticleEffect
import net.minecraft.recipe.BrewingRecipeRegistry
import net.minecraft.recipe.RecipeManager
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.resource.featuretoggle.FeatureSet
import net.minecraft.scoreboard.Scoreboard
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.collection.Pool
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.GlobalPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.BlockView
import net.minecraft.world.Difficulty
import net.minecraft.world.MutableWorldProperties
import net.minecraft.world.World
import net.minecraft.world.WorldProperties
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.BiomeKeys
import net.minecraft.world.border.WorldBorder
import net.minecraft.world.chunk.Chunk
import net.minecraft.world.chunk.ChunkManager
import net.minecraft.world.chunk.ChunkStatus
import net.minecraft.world.chunk.light.LightingProvider
import net.minecraft.world.dimension.DimensionTypes
import net.minecraft.world.entity.EntityLookup
import net.minecraft.world.event.GameEvent
import net.minecraft.world.explosion.ExplosionBehavior
import net.minecraft.world.tick.QueryableTickScheduler
import net.minecraft.world.tick.TickManager
import java.util.function.BooleanSupplier

class DummyWorld(registryManager: DynamicRegistryManager, val featureSet: FeatureSet) : World(DummyProperties(), null, registryManager, registryManager.getEntryOrThrow(DimensionTypes.OVERWORLD), false, false, 0, 0) {
    private val chunkManager = DummyChunkManager()
    override fun getPlayers(): MutableList<out PlayerEntity> {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getBrightness(direction: Direction?, shaded: Boolean): Float {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getGeneratorStoredBiome(biomeX: Int, biomeY: Int, biomeZ: Int): RegistryEntry<Biome> {
        return registryManager.getEntryOrThrow(BiomeKeys.PLAINS)
    }

    override fun getSeaLevel(): Int {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getEnabledFeatures() = featureSet

    override fun getBlockTickScheduler(): QueryableTickScheduler<Block> {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getFluidTickScheduler(): QueryableTickScheduler<Fluid> {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getChunkManager(): ChunkManager = chunkManager

    override fun playSound(
        source: Entity?,
        x: Double,
        y: Double,
        z: Double,
        sound: RegistryEntry<SoundEvent>?,
        category: SoundCategory?,
        volume: Float,
        pitch: Float,
        seed: Long,
    ) {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun syncWorldEvent(source: Entity?, eventId: Int, pos: BlockPos?, data: Int) {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun emitGameEvent(event: RegistryEntry<GameEvent>?, emitterPos: Vec3d?, emitter: GameEvent.Emitter?) {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun updateListeners(pos: BlockPos?, oldState: BlockState?, newState: BlockState?, flags: Int) {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun playSoundFromEntity(
        source: Entity?,
        entity: Entity?,
        sound: RegistryEntry<SoundEvent>?,
        category: SoundCategory?,
        volume: Float,
        pitch: Float,
        seed: Long,
    ) {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun createExplosion(
        entity: Entity?,
        damageSource: DamageSource?,
        behavior: ExplosionBehavior?,
        x: Double,
        y: Double,
        z: Double,
        power: Float,
        createFire: Boolean,
        explosionSourceType: ExplosionSourceType?,
        smallParticle: ParticleEffect?,
        largeParticle: ParticleEffect?,
        blockParticles: Pool<BlockParticleEffect?>?,
        soundEvent: RegistryEntry<SoundEvent?>?,
    ) {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun asString(): String {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun setSpawnPoint(spawnPoint: WorldProperties.SpawnPoint) {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getSpawnPoint(): WorldProperties.SpawnPoint {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getEntityById(id: Int): Entity? {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getEnderDragonParts(): MutableCollection<EnderDragonPart> {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getTickManager(): TickManager {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getMapState(id: MapIdComponent?): MapState? {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun setBlockBreakingInfo(entityId: Int, pos: BlockPos?, progress: Int) {
        throw NotImplementedError("Not supported by dummy")
    }

    private val dummyScoreboard = Scoreboard()

    override fun getScoreboard() = dummyScoreboard

    override fun getRecipeManager(): RecipeManager {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getEntityLookup(): EntityLookup<Entity> {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getBrewingRecipeRegistry(): BrewingRecipeRegistry {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getFuelRegistry(): FuelRegistry {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getWorldBorder(): WorldBorder {
        throw NotImplementedError("Not supported by dummy")
    }

    class DummyProperties : MutableWorldProperties {
        private var spawnPoint = WorldProperties.SpawnPoint(GlobalPos(OVERWORLD, BlockPos.ORIGIN), 0f, 0f)
        override fun getSpawnPoint(): WorldProperties.SpawnPoint = spawnPoint
        override fun getTime() = 0L
        override fun getTimeOfDay() = 0L
        override fun isThundering() = false
        override fun isRaining() = false
        override fun setRaining(raining: Boolean) { }
        override fun isHardcore() = false
        override fun getDifficulty() = Difficulty.NORMAL
        override fun isDifficultyLocked() = false
        override fun setSpawnPoint(spawnPoint: WorldProperties.SpawnPoint) {
            this.spawnPoint = spawnPoint
        }
    }

    inner class DummyChunkManager : ChunkManager() {
        override fun getChunk(x: Int, z: Int, leastStatus: ChunkStatus, create: Boolean): Chunk? = null
        override fun tick(shouldKeepTicking: BooleanSupplier, tickChunks: Boolean) { }
        override fun getDebugString(): String = "dummy"
        override fun getLoadedChunkCount(): Int = 0
        override fun getLightingProvider(): LightingProvider = LightingProvider.DEFAULT
        override fun getWorld(): BlockView? = this@DummyWorld
    }
}