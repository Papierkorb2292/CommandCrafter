package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.resources.Identifier
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.papierkorb2292.command_crafter.editor.processing.PackContentFileType
import net.papierkorb2292.command_crafter.parser.helper.RawResource
import net.papierkorb2292.command_crafter.parser.languages.VanillaLanguage
import java.io.BufferedReader

object McfunctionZipCreatorProcessor : RawZipResourceCreator.DataTypeProcessor {
    override val type: String
        get() = PackContentFileType.FUNCTIONS_FILE_TYPE.contentTypePath

    override fun shouldProcess(args: DatapackBuildArgs) = !args.keepDirectives

    override fun process(
        args: DatapackBuildArgs,
        id: Identifier,
        content: BufferedReader,
        resourceCreator: RawZipResourceCreator,
        dispatcher: CommandDispatcher<SharedSuggestionProvider>,
        buildContext: CommandBuildContext,
    ) {
        val reader = DirectiveStringReader(FileMappingInfo(content.lines().toList()), dispatcher, resourceCreator)
        val resource = RawResource(RawResource.FUNCTION_TYPE)
        val source = Commands.createCompilationContext(args.permissions ?: LevelBasedPermissionSet.GAMEMASTER)
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
        dispatcher: CommandDispatcher<SharedSuggestionProvider>,
        buildContext: CommandBuildContext,
    ) {
        process(args, id, content, RawZipResourceCreator(), dispatcher, buildContext)
    }
}