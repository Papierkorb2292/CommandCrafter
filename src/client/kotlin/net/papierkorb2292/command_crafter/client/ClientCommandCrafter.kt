package net.papierkorb2292.command_crafter.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ParseResults
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.ParsedArgument
import com.mojang.brigadier.context.ParsedCommandNode
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.tree.ArgumentCommandNode
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.font.FontManager
import net.minecraft.client.renderer.PostChainConfig
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.client.renderer.texture.atlas.SpriteSources
import net.minecraft.client.resources.WaypointStyle
import net.minecraft.client.resources.metadata.language.LanguageMetadataSection
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.ArmorType
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.client.editor.DirectMinecraftClientConnection
import net.papierkorb2292.command_crafter.client.editor.processing.AnalyzingClientCommandSource
import net.papierkorb2292.command_crafter.editor.EditorConnectionManager
import net.papierkorb2292.command_crafter.editor.McFunctionAnalyzer
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.SocketEditorConnectionType
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.editor.processing.PackMetaAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.StringRangeTreeJsonResourceAnalyzer
import net.papierkorb2292.command_crafter.editor.processing.command_arguments.CommandArgumentAnalyzerService
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.PotentialSyntaxNode
import net.papierkorb2292.command_crafter.editor.processing.helper.completionItemsToSuggestions
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo
import net.papierkorb2292.command_crafter.parser.Language
import net.papierkorb2292.command_crafter.parser.helper.getContextAtCursor
import net.papierkorb2292.command_crafter.parser.helper.getNodeAtCursor
import net.papierkorb2292.command_crafter.parser.helper.limitCommandTreeForSource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.eclipse.lsp4j.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

object ClientCommandCrafter : ClientModInitializer {
    override fun onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register {
            // Delay initialization to ensure that the game is fully loaded when the dynamicRegistryManager is created
            initializeEditor()
        }
    }

    val defaultFeatureSet = FeatureFlags.REGISTRY.allFlags()

    var editorConnectionManager: EditorConnectionManager = EditorConnectionManager(
        SocketEditorConnectionType(CommandCrafter.config.servicesPort),
        ClientDummyServerConnection(
            CommandDispatcher(), LevelBasedPermissionSet.NO_PERMISSIONS
        ),
        DirectMinecraftClientConnection,
        CommandCrafter.serviceLaunchers
    )

    private var loadedClientsideRegistries: LoadedClientsideRegistries? = null
    fun getLoadedClientsideRegistries() = loadedClientsideRegistries ?:
        throw IllegalStateException("getLoadedClientsideRegistries called before registries were loaded")

    private fun initializeEditor() {
        CommandCrafter.registerDynamicRegistries()
        CommandCrafter.registerRegistryTags()

        MinecraftLanguageServer.addAnalyzer(McFunctionAnalyzer({ languageServer ->
            AnalyzingClientCommandSource(Minecraft.getInstance(), languageServer.dynamicRegistryManager)
        }, { analyzingResult ->
            val finalResult = analyzingResult.copyActual()
            finalResult.addPotentialSyntaxNode(
                AnalyzingResult.LANGUAGE_COMPLETION_CHANNEL,
                StringRange(0, finalResult.mappingInfo.accumulatedLineLengths.last()),
                object : PotentialSyntaxNode {
                    override fun getCompletions(cursor: Int, context: CompletionContext?): CompletableFuture<List<CompletionItem>>? {
                        AnalyzingClientCommandSource.allowServersideCompletions.set(true)
                        return analyzingResult.getCompletions(cursor, context)
                    }
                }
            )
            finalResult
        }))
        MinecraftLanguageServer.addAnalyzer(PackMetaAnalyzer(LanguageMetadataSection.TYPE))

        StringRangeTreeJsonResourceAnalyzer.addJsonAnalyzers(clientsideJsonResourceCodecs)

        AnalyzingClientCommandSource.setupCompletionContextSetter()

        LoadedClientsideRegistries.load(Minecraft.getInstance()).thenApply { loadedClientsideRegistries ->
            ClientCommandCrafter.loadedClientsideRegistries = loadedClientsideRegistries

            val registryWrapperLookup = loadedClientsideRegistries.combinedRegistries.compositeAccess()
            fun setDefaultServerConnection() {
                val rootNode = limitCommandTreeForSource(
                    Commands(
                        Commands.CommandSelection.ALL,
                        CommandBuildContext.simple(registryWrapperLookup, defaultFeatureSet)
                    ), Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER)
                )
                CommandCrafter.removeLiteralsStartingWithForwardsSlash(rootNode)
                editorConnectionManager.minecraftServerConnection = ClientDummyServerConnection(
                    CommandDispatcher(rootNode),
                    LevelBasedPermissionSet.GAMEMASTER
                )
            }
            setDefaultServerConnection()
            ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
                setDefaultServerConnection()
                // Remove tags that were received from the server and apply the tags known to the client
                loadedClientsideRegistries?.applyTagsAndComponents()
            }
        }

        NetworkServerConnection.registerPacketHandlers()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            NetworkServerConnection.requestAndCreate().thenAccept {
                editorConnectionManager.minecraftServerConnection = it
                editorConnectionManager.showMessage(MessageParams(MessageType.Info, "Successfully connected to Minecraft server"))
            }.exceptionally { exception ->
                val coreException = if(exception is CompletionException) exception.cause!! else exception
                val message = "Connecting to Minecraft server failed, keeping clientside connection: ${coreException.message}"
                CommandCrafter.LOGGER.warn(message)
                editorConnectionManager.showMessage(MessageParams(MessageType.Warning, message))
                null
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            editorConnectionManager.leave()
        }

        editorConnectionManager.startServer()

        CommandCrafter.config.addServicesPortChangedListener {
            editorConnectionManager.stopServer()
            editorConnectionManager = editorConnectionManager.copyForNewConnectionAcceptor(SocketEditorConnectionType(it))
            editorConnectionManager.startServer()
        }
    }

    fun getCustomIngameSuggestions(input: String, cursor: Int): CompletableFuture<Suggestions> {
        val emptySuggestions = CompletableFuture.completedFuture(Suggestions(StringRange.at(cursor), listOf()))
        if(!CommandCrafter.config.addIngameSuggestions)
            return emptySuggestions
        // Make sure to use CommandCrafter's dispatcher, which has extra info
        val dispatcher = editorConnectionManager.minecraftServerConnection.commandDispatcher
        val directiveReader = DirectiveStringReader(
            FileMappingInfo(listOf(input)),
            dispatcher,
            AnalyzingResourceCreator(null, "", editorConnectionManager.minecraftServerConnection.dynamicRegistryManager)
        )
        directiveReader.enterClosure(Language.TopLevelClosure(VanillaLanguage()))
        if(directiveReader.canRead() && directiveReader.peek() == '/')
            directiveReader.skip()
        val analyzingResult = AnalyzingResult(directiveReader.fileMappingInfo, Position())
        val parsed = directiveReader.dispatcher.parse(directiveReader, AnalyzingClientCommandSource(Minecraft.getInstance(), editorConnectionManager.minecraftServerConnection.dynamicRegistryManager))

        val (parsedNode, context) = getContextForIngameSuggestions(parsed, directiveReader, cursor) ?: return emptySuggestions
        val node = parsedNode.node
        if(node !is ArgumentCommandNode<*, *>)
            return emptySuggestions
        val analyzer = CommandArgumentAnalyzerService.getAnalyzerForType(node.type::class.java)!!
        directiveReader.cursor = parsedNode.range.start
        try {
            VanillaLanguage.callArgumentAnalyzerUnchecked(
                analyzer,
                context,
                node.type,
                parsedNode.range,
                node.name,
                directiveReader,
                analyzingResult,
            )
        } catch(e: Exception) {
            CommandCrafter.LOGGER.debug("Error while analyzing command node ${node.name} for chat", e)
        }

        val completionsFuture = analyzingResult.getCompletions(cursor, null) ?: return emptySuggestions
        return completionsFuture.thenApply { completionItems ->
            completionItemsToSuggestions(completionItems, directiveReader, cursor)
        }
    }

    fun getContextForIngameSuggestions(parseResults: ParseResults<SharedSuggestionProvider>, reader: DirectiveStringReader<AnalyzingResourceCreator>, cursor: Int): Pair<ParsedCommandNode<SharedSuggestionProvider>, CommandContext<SharedSuggestionProvider>>? {
        val command = parseResults.context.build(reader.string)
        var context = command.getContextAtCursor(cursor)
        var parsedNode = context?.getNodeAtCursor(cursor)
        if(parsedNode == null) {
            // Cursor is at the end of the parsed command, use the best matching child of the last node
            val lastContext = command.lastChild
            val lastContextBuilder = parseResults.context.lastChild
            var lastNode = lastContext.nodes.lastOrNull()?.node
            reader.cursor = lastContext.range.end
            if(lastNode == null) {
                lastNode = lastContext.rootNode
            } else if(!reader.canRead() || reader.read() != ' ') // Space has to be skipped if it's not the first argument in this context
                return null
            val start = reader.cursor
            for(child in lastNode.children) {
                if(child !is ArgumentCommandNode<*, *>)
                    continue // Got nothing to add to literals
                reader.cursor = start
                val newContext = lastContextBuilder.copy()
                try {
                    val argument = child.type.parse(reader, newContext.source)
                    newContext.withArgument(child.name, ParsedArgument(start, reader.cursor, argument))
                } catch(_: Exception) { }
                if(parsedNode == null || reader.cursor > parsedNode.range.end) {
                    parsedNode = ParsedCommandNode(child, StringRange(start, reader.cursor))
                    context = newContext.build(parseResults.reader.string)
                }
            }

            // Extend node range to the end of the string, since this is the last node and it shouldn't matter where the vanilla parser stopped
            if(parsedNode != null)
                parsedNode = ParsedCommandNode(parsedNode.node, StringRange(parsedNode.range.start, reader.string.length))
        }
        return if(parsedNode != null && context != null) parsedNode to context else null
    }

    val clientsideJsonResourceCodecs = mutableMapOf(
        PackContentFileType.ATLASES_FILE_TYPE to SpriteSources.FILE_CODEC,
        PackContentFileType.BLOCKSTATES_FILE_TYPE to BlockStateModelDispatcher.CODEC,
        PackContentFileType.EQUIPMENT_FILE_TYPE to ArmorType.CODEC,
        PackContentFileType.FONTS_FILE_TYPE to FontManager.FontDefinitionFile.CODEC,
        PackContentFileType.ITEMS_FILE_TYPE to ClientItem.CODEC,
        PackContentFileType.POST_EFFECTS_FILE_TYPE to PostChainConfig.CODEC,
        PackContentFileType.WAYPOINT_STYLE_FILE_TYPE to WaypointStyle.CODEC,
    )

    var currentlyHoveredItem: ItemStack? = null
}