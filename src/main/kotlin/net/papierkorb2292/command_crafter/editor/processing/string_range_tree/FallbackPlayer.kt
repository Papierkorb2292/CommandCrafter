package net.papierkorb2292.command_crafter.editor.processing.string_range_tree

import com.mojang.authlib.GameProfile
import com.mojang.serialization.Codec
import net.minecraft.core.UUIDUtil
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.papierkorb2292.command_crafter.editor.processing.string_range_tree.DataObjectDecoding.Companion.getForDecoder
import net.papierkorb2292.command_crafter.helper.StringIdentifiableUnit
import java.util.*
import java.util.function.Consumer

class FallbackPlayer(level: Level, gameProfile: GameProfile) : Player(level, gameProfile) {
    companion object {
        private val enderPearlIdCodec: Codec<*> =
            StringRepresentable.fromValues {
                arrayOf<StringRepresentable>(StringIdentifiableUnit("minecraft:ender_pearl"))
            }

        fun readSpecialTags(input: ValueInput) {
            if(input !is DynamicOpsReadView<*>)
                return
            val enderPearls = input.childrenListOrEmpty(ServerPlayer.ENDER_PEARLS_TAG)
            val dataObjectDecoding = getForDecoder(input.dynamic.getOps()) ?: return
            val enderPearl = dataObjectDecoding.dummyEntities[EntityTypes.ENDER_PEARL]
            for(enderPearlInput in enderPearls) {
                enderPearlInput.read(
                    ServerPlayer.ENDER_PEARL_DIMENSION_TAG,
                    Level.RESOURCE_KEY_CODEC
                )
                enderPearlInput.read("id", enderPearlIdCodec)
                if(enderPearl != null) {
                    dataObjectDecoding.analyzeEntity(
                        enderPearl,
                        enderPearlInput as DynamicOpsReadView<*>,
                        true
                    )
                }
            }

            val rootVehicleInput = input.childOrEmpty("RootVehicle")
            rootVehicleInput.read("Attach", UUIDUtil.CODEC)
            rootVehicleInput.child("Entity")
                .ifPresent(Consumer { entityInput: ValueInput -> dataObjectDecoding.readDispatchingEntity(entityInput as DynamicOpsReadView<*>) })
        }
    }

    override fun gameMode() = GameType.DEFAULT_MODE

    override fun readAdditionalSaveData(input: ValueInput) {
        super.readAdditionalSaveData(input)
        readSpecialTags(input)
        // Remaining tags are filled in by CommandCrafterFallbackPlayerSaveDataExtractorMixinCoprocessor
    }
}