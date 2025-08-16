package net.papierkorb2292.command_crafter.parser

import com.fasterxml.jackson.annotation.JsonIgnore
import com.google.common.collect.ImmutableMap
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.registry.Registry
import net.minecraft.registry.tag.TagEntry
import net.minecraft.registry.tag.TagGroupLoader
import net.minecraft.server.DataPackContents
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.server.function.FunctionBuilder
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.parser.helper.FileSourceContainer
import net.papierkorb2292.command_crafter.parser.helper.InlineTagFunctionIdContainer
import java.util.*

class ParsedResourceCreator(
    val functionId: Identifier,
    val functionPackId: String,
) {
    companion object {
        val PLACEHOLDER_ID = Identifier.of("command_crafter", "placeholder")
        val RESOURCE_CREATOR_UNAVAILABLE_EXCEPTION = SimpleCommandExceptionType(Text.of("Attempted to use a feature requiring a ParsedResourceCreator, but it isn't available in that context."))

        /**
         * Keeps track of current pending tag loads and their tag data. Used to resolve and add inline tags from functions.
         *
         * For each entry, the PendingTagLoads are mapped to their Registry and RegistryTags by index.
         */
        val PENDING_TAG_LOADS = WeakHashMap<List<Registry.PendingTagLoad<*>>, MutableList<Pair<Registry<*>, TagGroupLoader.RegistryTags<*>>>>()

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
                    if(lines != null && fileId != null)
                        generatedChildFunction.`command_crafter$setFileSource`(lines, fileId)
                }
                functionMapBuilder.put(functionId, generatedChildFunction)
                childFunction.idSetter(functionId)
            }
            resourceId = 0
            for(functionTag in resourceCreator.functionTags) {
                val tagId = resourceCreator.getPath(resourceId++)
                val entryList: MutableList<TagGroupLoader.TrackedEntry> = ArrayList()
                @Suppress("INFERRED_TYPE_VARIABLE_INTO_POSSIBLE_EMPTY_INTERSECTION")
                functionTagMap[tagId] = functionTag.resource.mapTo(entryList) {
                    val trackedEntry = TagGroupLoader.TrackedEntry(it, resourceCreator.functionPackId)
                    @Suppress("CAST_NEVER_SUCCEEDS")
                    (trackedEntry as InlineTagFunctionIdContainer).`command_crafter$setInlineTagFunctionId`(resourceCreator.functionId)
                    trackedEntry
                }
                functionTag.idSetter(tagId)
            }
        }
    }

    val functions: MutableList<AutomaticResource<FunctionBuilder<ServerCommandSource>>> = LinkedList()
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

    class AutomaticResource<T>(@JsonIgnore val idSetter: (Identifier) -> Unit, val resource: T)

    private fun getPath(id: Int) = Identifier.of(functionId.namespace, "${functionId.path}--$id--craftergen")

    interface ParseResourceContainer {
        fun `command_crafter$setResourceCreator`(resourceCreator: ParsedResourceCreator?)
        fun `command_crafter$getResourceCreator`(): ParsedResourceCreator?
    }

    interface ParseResourceContextContainer {
        fun `command_crafter$setResourceCreatorContext`(dataPackContents: DataPackContents?)
        fun `command_crafter$getResourceCreatorContext`(): DataPackContents?
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
