package net.papierkorb2292.command_crafter.parser

import com.google.common.collect.ImmutableMap
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.datafixers.util.Pair
import net.minecraft.loot.LootDataType
import net.minecraft.loot.LootTable
import net.minecraft.loot.condition.LootCondition
import net.minecraft.loot.function.LootFunction
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.registry.tag.TagEntry
import net.minecraft.registry.tag.TagGroupLoader
import net.minecraft.registry.tag.TagManagerLoader
import net.minecraft.server.DataPackContents
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.server.function.FunctionBuilder
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.mixin.parser.DataPackContentsAccessor
import net.papierkorb2292.command_crafter.parser.helper.FileSourceContainer
import java.util.*
import java.util.stream.Collectors

class ParsedResourceCreator(
    val functionId: Identifier,
    val dataPackContents: DataPackContents,
) {
    companion object {
        val PLACEHOLDER_ID = Identifier("command_crafter", "placeholder")
        val RESOURCE_CREATOR_UNAVAILABLE_EXCEPTION = SimpleCommandExceptionType(Text.of("Attempted to use a feature requiring a ParsedResourceCreator, but it isn't available in that context."))

        fun <T> addResourceCreatorToFunction(
            function: CommandFunction<T>,
            resourceCreator: ParsedResourceCreator?,
        ): CommandFunction<T> {
            (function as ParseResourceContainer).`command_crafter$setResourceCreator`(resourceCreator)
            return function
        }

        fun createResources(
            function: CommandFunction<ServerCommandSource>,
            functionMapBuilder: ImmutableMap.Builder<Identifier, CommandFunction<ServerCommandSource>>,
            functionTagMap: MutableMap<Identifier, MutableList<TagGroupLoader.TrackedEntry>>,
        ) {
            val resourceCreator = (function as ParseResourceContainer).`command_crafter$getResourceCreator`() ?: return
            var resourceId = 0
            for(childFunction in resourceCreator.functions) {
                val functionId = resourceCreator.getPath(resourceId++)
                val generatedChildFunction = childFunction.resource.toCommandFunction(functionId)
                if(function is FileSourceContainer && generatedChildFunction is FileSourceContainer) {
                    val lines = function.`command_crafter$getFileSourceLines`()
                    val fileId = function.`command_crafter$getFileSourceId`()
                    val fileType= function.`command_crafter$getFileSourceType`()
                    if(lines != null && fileId != null && fileType != null)
                        generatedChildFunction.`command_crafter$setFileSource`(lines, fileId, fileType)
                }
                functionMapBuilder.put(functionId, generatedChildFunction)
                childFunction.idSetter(functionId)
            }
            resourceId = 0
            for(functionTag in resourceCreator.functionTags) {
                val tagId = resourceCreator.getPath(resourceId++)
                val entryList: MutableList<TagGroupLoader.TrackedEntry> = ArrayList()
                functionTagMap[tagId] = functionTag.resource.mapTo(entryList) { TagGroupLoader.TrackedEntry(it, "FUNCTION: $functionTag") }
                functionTag.idSetter(tagId)
            }
            resourceId = 0
            val registryTagsList: List<TagManagerLoader.RegistryTags<*>> = (resourceCreator.dataPackContents as DataPackContentsAccessor).registryTagManager.registryTags
            val dataPackRefresher = resourceCreator.dataPackContents as DataPackRefresher
            for(registryTag in resourceCreator.registryTags) {
                for(registryTags in registryTagsList) {
                    if(registryTags.key == registryTag.resource.registry.key) {
                        val tagId = resolveRegistryTag(
                            registryTag.resource.entries,
                            registryTags,
                            resourceCreator,
                            resourceId++
                        )
                        dataPackRefresher.`command_crafter$addCallback` { registryTag.idSetter(tagId) }
                        break
                    }
                }
            }
            val newLootResources: MutableMap<Identifier, Pair<LootDataType<*>, *>> = HashMap() //This isn't nice, but Mojang started it!
            resourceId = 0
            for(lootCondition in resourceCreator.lootConditions) {
                val conditionId = resourceCreator.getPath(resourceId++)
                newLootResources[conditionId] = Pair.of(LootDataType.PREDICATES, lootCondition.resource)
                lootCondition.idSetter(conditionId)
            }
            resourceId = 0
            for(lootFunction in resourceCreator.lootFunctions) {
                val functionId = resourceCreator.getPath(resourceId++)
                newLootResources[functionId] = Pair.of(LootDataType.ITEM_MODIFIERS, lootFunction.resource)
                lootFunction.idSetter(functionId)
            }
            resourceId = 0
            for(lootTable in resourceCreator.lootTables) {
                val tableId = resourceCreator.getPath(resourceId++)
                newLootResources[tableId] = Pair.of(LootDataType.LOOT_TABLES, lootTable.resource())
                lootTable.idSetter(tableId)
            }
            @Suppress("UNCHECKED_CAST")
            (resourceCreator.dataPackContents.lootManager as VanillaResourceContainer<Pair<LootDataType<*>, *>>).`command_crafter$addAllResources`(newLootResources)
        }

        private val missingReferencesException = Dynamic2CommandExceptionType { sourceFunction: Any, missing: Any ->
            Text.of("Couldn't load function $sourceFunction, as a registry tag created by it is missing following references: $missing")
        }
        private fun <T> resolveRegistryTag(
            entries: Collection<TagEntry>,
            registryTags: TagManagerLoader.RegistryTags<T>,
            resourceCreator: ParsedResourceCreator,
            id: Int,
        ): Identifier {
            val valueRegistryKey = registryTags.key
            @Suppress("UNCHECKED_CAST")
            val valueRegistry = Registries.REGISTRIES.get(valueRegistryKey.value) as Registry<T>
            val tagId = resourceCreator.getPath(id)
            val resolvedEntries: MutableList<RegistryEntry<T>> = ArrayList()
            val valueGetter = object : TagEntry.ValueGetter<RegistryEntry<T>> {
                override fun direct(id: Identifier?): RegistryEntry<T>?
                    = valueRegistry.getEntry(RegistryKey.of(valueRegistryKey, id)).orElse(null)

                override fun tag(id: Identifier?): MutableCollection<RegistryEntry<T>>?
                    = registryTags.tags[id]
            }
            val missingReferences: MutableList<TagEntry> = ArrayList()
            for(entry in entries) {
                if(!entry.resolve(valueGetter, resolvedEntries::add)) {
                    missingReferences += entry
                }
            }
            if(missingReferences.isNotEmpty()) {
                throw missingReferencesException.create(
                    resourceCreator.functionId,
                    missingReferences.stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining(", "))
                )
            }
            registryTags.tags[tagId] = resolvedEntries
            return tagId
        }
    }

    val functions: MutableList<AutomaticResource<FunctionBuilder<ServerCommandSource>>> = LinkedList()
    val lootTables: MutableList<AutomaticResource<() -> LootTable>> = LinkedList()
    val lootFunctions: MutableList<AutomaticResource<LootFunction>> = LinkedList()
    val lootConditions: MutableList<AutomaticResource<LootCondition>> = LinkedList()
    val registryTags: MutableList<AutomaticResource<ParsedTag<*>>> = LinkedList()
    val functionTags: MutableList<AutomaticResource<Collection<TagEntry>>> = LinkedList()

    val originResourceIdSetEventStack = LinkedList<((Identifier) -> Unit) -> Unit>()
    val originResourceInfoSetEventStack = LinkedList<((ResourceStackInfo) -> Unit) -> Unit>()

    fun addOriginResource(): (ResourceStackInfo) -> Unit {
        val listeners: MutableList<(ResourceStackInfo) -> Unit> = ArrayList()
        originResourceIdSetEventStack.push { callback ->
            listeners.add { info ->
                callback(info.id)
            }
        }
        originResourceInfoSetEventStack.push(listeners::add)

        return {
            for(listener in listeners) {
                listener(it)
            }
        }
    }

    fun popOriginResource() {
        originResourceIdSetEventStack.pop()
        originResourceInfoSetEventStack.pop()
    }

    class AutomaticResource<T>(val idSetter: (Identifier) -> Unit, val resource: T)
    class ParsedTag<T>(val registry: DynamicRegistryManager.Entry<T>, val entries: Collection<TagEntry>)

    private fun getPath(id: Int) = Identifier(functionId.namespace, "${functionId.path}--$id--craftergen")

    interface ParseResourceContainer {
        fun `command_crafter$setResourceCreator`(resourceCreator: ParsedResourceCreator?)
        fun `command_crafter$getResourceCreator`(): ParsedResourceCreator?
    }

    interface ParseResourceContextContainer {
        fun `command_crafter$setResourceCreatorContext`(dataPackContents: DataPackContents?)
        fun `command_crafter$getResourceCreatorContext`(): DataPackContents?
    }

    interface VanillaResourceContainer<Resource> {
        fun `command_crafter$addAllResources`(newResources: Map<Identifier, Resource>)
    }

    interface DataPackRefresher {
        fun `command_crafter$addCallback`(callback: () -> Unit)
    }

    class ResourceStackInfo(val id: Identifier, val range: StringRange)
    class ResourceInfoSetterWrapper(val infoSetter: (ResourceStackInfo) -> Unit, var range: StringRange = StringRange.at(0)): (Identifier) -> Unit {
        override fun invoke(id: Identifier) {
            infoSetter(ResourceStackInfo(id, range))
        }
    }
}
