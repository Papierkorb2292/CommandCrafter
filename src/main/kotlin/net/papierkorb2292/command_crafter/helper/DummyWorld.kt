package net.papierkorb2292.command_crafter.helper

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.block.entity.FuelValues
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import net.minecraft.core.particles.ExplosionParticleInfo
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.item.alchemy.PotionBrewing
import net.minecraft.world.item.crafting.RecipeAccess
import net.minecraft.core.RegistryAccess
import net.minecraft.core.Holder
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.scores.Scoreboard
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.random.WeightedList
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.GlobalPos
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.Difficulty
import net.minecraft.world.level.storage.WritableLevelData
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelData
import net.minecraft.world.attribute.EnvironmentAttributeSystem
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.border.WorldBorder
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkSource
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.entity.LevelEntityGetter
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.ExplosionDamageCalculator
import net.minecraft.world.ticks.LevelTickAccess
import net.minecraft.world.TickRateManager
import java.util.function.BooleanSupplier

class DummyWorld(registryManager: RegistryAccess, val featureSet: FeatureFlagSet) : Level(DummyProperties(), OVERWORLD, registryManager, registryManager.getOrThrow(
    BuiltinDimensionTypes.OVERWORLD), false, false, 0, 0) {
    private val chunkManager = DummyChunkManager()
    override fun players(): MutableList<out Player> = mutableListOf()

    override fun getShade(direction: Direction, shaded: Boolean) = 0f

    override fun getUncachedNoiseBiome(biomeX: Int, biomeY: Int, biomeZ: Int): Holder<Biome> {
        return registryAccess().getOrThrow(Biomes.PLAINS)
    }

    override fun getSeaLevel() = 64

    override fun enabledFeatures() = featureSet

    override fun getBlockTicks(): LevelTickAccess<Block> {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getFluidTicks(): LevelTickAccess<Fluid> {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getChunkSource(): ChunkSource = chunkManager

    override fun playSeededSound(
        source: Entity?,
        x: Double,
        y: Double,
        z: Double,
        sound: Holder<SoundEvent>,
        category: SoundSource,
        volume: Float,
        pitch: Float,
        seed: Long,
    ) { }

    override fun levelEvent(source: Entity?, eventId: Int, pos: BlockPos, data: Int) { }

    override fun gameEvent(event: Holder<GameEvent>, emitterPos: Vec3, emitter: GameEvent.Context) { }

    override fun sendBlockUpdated(pos: BlockPos, oldState: BlockState, newState: BlockState, flags: Int) { }

    override fun playSeededSound(
        source: Entity?,
        entity: Entity,
        sound: Holder<SoundEvent>,
        category: SoundSource,
        volume: Float,
        pitch: Float,
        seed: Long,
    ) { }

    override fun explode(
        entity: Entity?,
        damageSource: DamageSource?,
        behavior: ExplosionDamageCalculator?,
        x: Double,
        y: Double,
        z: Double,
        power: Float,
        createFire: Boolean,
        explosionSourceType: ExplosionInteraction,
        smallParticle: ParticleOptions,
        largeParticle: ParticleOptions,
        blockParticles: WeightedList<ExplosionParticleInfo>,
        soundEvent: Holder<SoundEvent>,
    ) { }

    override fun gatherChunkSourceStats() = "DummyWorld"

    override fun setRespawnData(spawnPoint: LevelData.RespawnData) {
        levelData.setSpawn(spawnPoint)
    }

    override fun getRespawnData(): LevelData.RespawnData {
        return levelData.respawnData
    }

    override fun getEntity(id: Int) = null

    override fun dragonParts(): MutableCollection<EnderDragonPart> {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun tickRateManager(): TickRateManager {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getMapData(id: MapId): MapItemSavedData {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun destroyBlockProgress(entityId: Int, pos: BlockPos, progress: Int) {
        throw NotImplementedError("Not supported by dummy")
    }

    private val dummyScoreboard = Scoreboard()

    override fun getScoreboard() = dummyScoreboard

    override fun recipeAccess(): RecipeAccess {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getEntities(): LevelEntityGetter<Entity> {
        throw NotImplementedError("Not supported by dummy")
    }

    private val dummyEnvironmentAttributes = EnvironmentAttributeSystem.builder().build()

    override fun environmentAttributes(): EnvironmentAttributeSystem = dummyEnvironmentAttributes

    override fun potionBrewing(): PotionBrewing {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun fuelValues(): FuelValues {
        throw NotImplementedError("Not supported by dummy")
    }

    override fun getWorldBorder(): WorldBorder {
        throw NotImplementedError("Not supported by dummy")
    }

    class DummyProperties : WritableLevelData {
        private var spawnPoint = LevelData.RespawnData(GlobalPos(OVERWORLD, BlockPos.ZERO), 0f, 0f)
        override fun getRespawnData(): LevelData.RespawnData = spawnPoint
        override fun getGameTime() = 0L
        override fun getDayTime() = 0L
        override fun isThundering() = false
        override fun isRaining() = false
        override fun setRaining(raining: Boolean) { }
        override fun isHardcore() = false
        override fun getDifficulty() = Difficulty.NORMAL
        override fun isDifficultyLocked() = false
        override fun setSpawn(spawnPoint: LevelData.RespawnData) {
            this.spawnPoint = spawnPoint
        }
    }

    inner class DummyChunkManager : ChunkSource() {
        override fun getChunk(x: Int, z: Int, leastStatus: ChunkStatus, create: Boolean): ChunkAccess? = null
        override fun tick(shouldKeepTicking: BooleanSupplier, tickChunks: Boolean) { }
        override fun gatherStats(): String = "dummy"
        override fun getLoadedChunksCount(): Int = 0
        override fun getLightEngine(): LevelLightEngine = LevelLightEngine.EMPTY
        override fun getLevel(): BlockGetter = this@DummyWorld
    }
}