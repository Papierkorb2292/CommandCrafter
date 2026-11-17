package net.papierkorb2292.command_crafter.editor.processing

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.RootCommandNode
import it.unimi.dsi.fastutil.objects.*
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SuggestionBox
import net.minecraft.commands.suggestions.SuggestionContext
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.game.ClientboundCommandsPacket
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.world.flag.FeatureFlagSet
import net.papierkorb2292.command_crafter.mixin.editor.ClientboundCommandsPacketAccessor
import net.papierkorb2292.command_crafter.mixin.editor.CommandBoxAccessor
import net.papierkorb2292.command_crafter.mixin.editor.SuggestionBoxAccessor
import net.papierkorb2292.command_crafter.networking.list
import net.papierkorb2292.command_crafter.parser.helper.limitCommandTreeForSource
import java.util.*

class AnalyzingCommandTree(
    val suggestionBox: SuggestionBox,
    private val additionalArgumentData: IdentityHashMap<ArgumentType<*>, out Map<AdditionalArgumentTypeData.AdditionalDataType<*>, Any>>,
) {
    companion object {
        val BUILDER: ScopedValue<Builder> = ScopedValue.newInstance()
        val SERIALIZED_ARGUMENT_DATA: ScopedValue<Map<Int, List<AdditionalArgumentTypeData.AdditionalData<*>>>> = ScopedValue.newInstance()

        val EMPTY = AnalyzingCommandTree(SuggestionBox.fromFullTree(CommandDispatcher()), IdentityHashMap())

        val STREAM_CODEC = StreamCodec.composite(
            ClientboundCommandsPacket.STREAM_CODEC,
            AnalyzingCommandTree::getCommandsPacket,
            ByteBufCodecs.map(
                ::HashMap,
                ByteBufCodecs.INT,
                AdditionalArgumentTypeData.ARGUMENT_DATA_STREAM_CODEC.list()
            ),
            AnalyzingCommandTree::getRawAdditionalDataMap,
        ) { clientboundCommandsPacket, additionalArgumentData ->
            val builder = Builder()
            // Int ids need to be mapped back to argument type instances. This is done with a mixin to the NodeResolver
            val suggestionBox = ScopedValue.where(BUILDER, builder).where(SERIALIZED_ARGUMENT_DATA, additionalArgumentData).call<_, Nothing> {
                SuggestionBox.fromPacket(clientboundCommandsPacket)
            }
            builder.build(suggestionBox)
        }

        fun createVanilla(commandSelection: Commands.CommandSelection, featureFlags: FeatureFlagSet, permissions: PermissionSet): AnalyzingCommandTree {
            val builder = Builder()
            val dispatcher = CommandDispatcher<CommandSourceStack>()
            ScopedValue.where(BUILDER, builder).call<_, Nothing> {
                Commands.registerVanillaCommands(dispatcher, commandSelection, CommandBuildContext.simple(featureFlags))
            }
            limitCommandTreeForSource(dispatcher) { permissions }
            val suggestionBox = SuggestionBox.fromFullTree(dispatcher)
            return builder.build(suggestionBox)
        }
    }

    @Suppress("UNCHECKED_CAST")
    val dispatcher: CommandDispatcher<SuggestionContext> = (suggestionBox as CommandBoxAccessor<SuggestionContext>).dispatcher

    fun <T: Any> getAdditionalData(argumentType: ArgumentType<*>, type: AdditionalArgumentTypeData.AdditionalDataType<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return additionalArgumentData[argumentType]?.get(type) as T?
    }

    private fun getRawAdditionalDataMap(): Map<Int, List<AdditionalArgumentTypeData.AdditionalData<*>>> {
        fun <T: Any> createAdditionalDataUnsafe(type: AdditionalArgumentTypeData.AdditionalDataType<T>, value: Any): AdditionalArgumentTypeData.AdditionalData<T> {
            @Suppress("UNCHECKED_CAST")
            return AdditionalArgumentTypeData.AdditionalData(type, value as T)
        }

        val idMap = Reference2IntOpenHashMap<ArgumentType<*>>()
        enumerateNodes(dispatcher.root).forEach { (node, id) ->
            if(node is ArgumentCommandNode<*, *>) {
                idMap[node.type] = id
            }
        }

        val serializedData = mutableMapOf<Int, List<AdditionalArgumentTypeData.AdditionalData<*>>>()
        additionalArgumentData.forEach { (argumentType, data) ->
            if(!idMap.containsKey(argumentType))
                return@forEach
            val id = idMap.getInt(argumentType)
            serializedData[id] = data.map { (type, value) ->
                createAdditionalDataUnsafe(type, value)
            }
        }
        return serializedData
    }

    // Use custom ClientboundCommandsPacket creation, using Reference2Int instead of Object2Int, because otherwise additional data might be lost

    private fun <S : Any> enumerateNodes(root: RootCommandNode<S>): Reference2IntMap<CommandNode<S>> {
        val nodeToId: Reference2IntMap<CommandNode<S>> = Reference2IntOpenHashMap()
        val queue: Queue<CommandNode<S>> = ArrayDeque()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.remove()
            if (!nodeToId.containsKey(node)) {
                val id = nodeToId.size
                nodeToId[node] = id
                queue.addAll(node.children)
                if(node.redirect != null)
                    queue.add(node.redirect)
            }
        }

        return nodeToId
    }

    private fun <S : Any> createEntries(
        nodeToId: Reference2IntMap<out CommandNode<out S>>,
        inspector: ClientboundCommandsPacket.NodeInspector<S>,
    ): List<ClientboundCommandsPacket.Entry> {
        val emptyMap = Object2IntMap.ofEntries<CommandNode<out S>>()
        val result = ObjectArrayList<ClientboundCommandsPacket.Entry>(nodeToId.size)
        result.size(nodeToId.size)

        for (entry in Reference2IntMaps.fastIterable(nodeToId)) {
            val packetEntry = ClientboundCommandsPacketAccessor.callCreateEntry(entry.key as CommandNode<out S>, inspector, emptyMap)
            // Handle children and redirect separately with the Reference2IntMap
            val redirect = if(entry.key.redirect != null) nodeToId.getInt(entry.key.redirect) else 0
            val children = entry.key.children.map { nodeToId.getInt(it) }.toIntArray()
            @Suppress("UNCHECKED_CAST")
            result[entry.intValue] = ClientboundCommandsPacket.Entry(packetEntry.stub, packetEntry.flags, redirect, children)
        }

        return result
    }

    private fun getCommandsPacket(): ClientboundCommandsPacket {
        val idMap = enumerateNodes(dispatcher.root)
        val entries = createEntries(idMap, SuggestionBoxAccessor.getCOMMAND_NODE_INSPECTOR())
        return ClientboundCommandsPacketAccessor.callInit(
            entries,
            idMap.getInt(dispatcher.root),
        )
    }

    class Builder {
        private val additionalArgumentData: IdentityHashMap<ArgumentType<*>, MutableMap<AdditionalArgumentTypeData.AdditionalDataType<*>, Any>> = IdentityHashMap()

        fun <T : Any> addData(argumentType: ArgumentType<*>, type: AdditionalArgumentTypeData.AdditionalDataType<T>, value: T) {
            val data = AdditionalArgumentTypeData.AdditionalData(type, value)
            val map = additionalArgumentData.computeIfAbsent(argumentType) { mutableMapOf() }
            map[data.type] = data.value
        }

        fun addData(argumentType: ArgumentType<*>, data: List<AdditionalArgumentTypeData.AdditionalData<*>>) {
            val map = additionalArgumentData.computeIfAbsent(argumentType) { IdentityHashMap() }
            data.forEach { entry ->
                map[entry.type] = entry.value
            }
        }

        fun build(suggestionBox: SuggestionBox): AnalyzingCommandTree {
            return AnalyzingCommandTree(suggestionBox, additionalArgumentData)
        }
    }
}