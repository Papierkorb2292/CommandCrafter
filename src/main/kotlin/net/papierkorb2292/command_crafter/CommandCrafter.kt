package net.papierkorb2292.command_crafter
import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer
import net.minecraft.registry.Registry
import net.minecraft.screen.ScreenTexts
import net.minecraft.server.command.CommandOutput
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.papierkorb2292.command_crafter.editor.MinecraftLanguageServer
import net.papierkorb2292.command_crafter.editor.MinecraftServerConnection
import net.papierkorb2292.command_crafter.editor.OpenFile
import net.papierkorb2292.command_crafter.editor.processing.SemanticResourceCreator
import net.papierkorb2292.command_crafter.editor.processing.helper.AnalyzingResult
import net.papierkorb2292.command_crafter.editor.processing.helper.FileAnalyseHandler
import net.papierkorb2292.command_crafter.parser.*
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import org.apache.logging.log4j.LogManager
import java.io.BufferedReader

class CommandCrafter: ModInitializer {
    companion object {
        const val MOD_ID = "command_crafter"
        val LOGGER = LogManager.getLogger(MOD_ID)
    }
    override fun onInitialize() {
        initializeEditor()
        initializeParser()
        LOGGER.info("Loaded CommandCrafter!")
    }

    private fun initializeEditor() =
        MinecraftLanguageServer.addAnalyzer(object: FileAnalyseHandler {
            override fun canHandle(file: OpenFile) = file.uri.endsWith(".mcfunction")

            override fun analyze(file: OpenFile, server: MinecraftServerConnection): AnalyzingResult {
                val lines = ArrayList<String>()
                file.lines.mapTo(lines) { it.toString() }
                val reader = DirectiveStringReader(lines, server.commandDispatcher, SemanticResourceCreator)

                val result = AnalyzingResult(lines)
                val source = ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, server.functionPermissionLevel, "", ScreenTexts.EMPTY, null, null)
                LanguageManager.analyse(reader, source, result, Language.TopLevelClosure(VanillaLanguage.NORMAL))
                return result
            }
        })

    private fun initializeParser() {
        Registry.register(LanguageManager.LANGUAGES, Identifier(VanillaLanguage.ID), VanillaLanguage::parseArguments)
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
                dispatcher: CommandDispatcher<ServerCommandSource>
            ) {
                val reader = DirectiveStringReader(content.lines().toList(), dispatcher, resourceCreator)
                val resource = RawResource(RawResource.FUNCTION_TYPE)
                val source = ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, args.permissionLevel ?: 2, "", ScreenTexts.EMPTY, null, null)
                LanguageManager.parseToVanilla(
                    reader,
                    source,
                    resource,
                    Language.TopLevelClosure(VanillaLanguage.NORMAL)
                )
                resourceCreator.addResource(id, resource)
            }

            override fun validate(
                args: DatapackBuildArgs,
                id: Identifier,
                content: BufferedReader,
                dispatcher: CommandDispatcher<ServerCommandSource>,
            ) {
                process(args, id, content, RawZipResourceCreator(), dispatcher)
            }
        }
    }
}