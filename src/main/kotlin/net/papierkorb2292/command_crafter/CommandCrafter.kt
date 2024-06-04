package net.papierkorb2292.command_crafter

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandSource
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer
import net.minecraft.registry.Registry
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.NetworkServerConnection
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingClientCommandSource
import net.papierkorb2292.command_crafter.editor.processing.AnalyzingResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.apache.logging.log4j.LogManager
import org.eclipse.lsp4j.Position
import java.io.BufferedReader


object CommandCrafter: ModInitializer {
    const val MOD_ID = "command_crafter"
    val LOGGER = LogManager.getLogger(MOD_ID)

    override fun onInitialize() {

        initializeEditor()
        NetworkServerConnection.registerServerPacketHandlers()

        initializeParser()
        LOGGER.info("Loaded CommandCrafter!")
    }

    private fun initializeEditor() =
        MinecraftLanguageServer.addAnalyzer(object: FileAnalyseHandler {
            override fun canHandle(file: OpenFile) = file.parsedUri.path.endsWith(".mcfunction")

            override fun analyze(
                file: OpenFile,
                languageServer: MinecraftLanguageServer
            ): AnalyzingResult {
                val lines = ArrayList<String>()
                file.lines.mapTo(lines) { it.toString() }
                val reader = DirectiveStringReader(FileMappingInfo(lines), languageServer.minecraftServer.commandDispatcher, AnalyzingResourceCreator(languageServer, file.uri))
                val result = AnalyzingResult(reader.fileMappingInfo, Position())
                reader.resourceCreator.resourceStack.push(AnalyzingResourceCreator.ResourceStackEntry(result))
                val source = AnalyzingClientCommandSource(MinecraftClient.getInstance())
                LanguageManager.analyse(reader, source, result, Language.TopLevelClosure(VanillaLanguage()))
                return result
            }
        })

    private fun initializeParser() {
        Registry.register(LanguageManager.LANGUAGES, Identifier(VanillaLanguage.ID), VanillaLanguage.VanillaLanguageType)
        ArgumentTypeRegistry.registerArgumentType(Identifier(MOD_ID, "datapack_build_args"), DatapackBuildArgs.DatapackBuildArgsArgumentType.javaClass, ConstantArgumentSerializer.of { -> DatapackBuildArgs.DatapackBuildArgsArgumentType })
        RawZipResourceCreator.DATA_TYPE_PROCESSORS += object : RawZipResourceCreator.DataTypeProcessor {
            override val type: String
                get() = "functions"

            override fun shouldProcess(args: DatapackBuildArgs) = !args.keepDirectives

            override fun process(
                args: DatapackBuildArgs,
                id: Identifier,
                content: BufferedReader,
                resourceCreator: RawZipResourceCreator,
                dispatcher: CommandDispatcher<CommandSource>,
            ) {
                val reader = DirectiveStringReader(FileMappingInfo(content.lines().toList()), dispatcher, resourceCreator)
                val resource = RawResource(RawResource.FUNCTION_TYPE)
                val source = ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, args.permissionLevel ?: 2, "", ScreenTexts.EMPTY, null, null)
                LanguageManager.parseToVanilla(
                    reader,
                    source,
                    resource,
                    Language.TopLevelClosure(VanillaLanguage())
                )
                resourceCreator.addResource(id, resource)
            }

            override fun validate(
                args: DatapackBuildArgs,
                id: Identifier,
                content: BufferedReader,
                dispatcher: CommandDispatcher<CommandSource>,
            ) {
                process(args, id, content, RawZipResourceCreator(), dispatcher)
            }
        }
    }
}