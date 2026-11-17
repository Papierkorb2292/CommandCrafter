package net.papierkorb2292.command_crafter.parser

import net.minecraft.commands.CommandBuildContext
import net.minecraft.resources.Identifier
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
    ) {
        val reader = DirectiveStringReader(FileMappingInfo(content.lines().toList()), resourceCreator)
        val resource = RawResource(RawResource.FUNCTION_TYPE)
        LanguageManager.parseToVanilla(
            reader,
            resource,
            Language.TopLevelClosure(VanillaLanguage())
        )
        resourceCreator.addResource(id, resource)
    }

    override fun validate(
        args: DatapackBuildArgs,
        id: Identifier,
        content: BufferedReader,
        resourceCreator: RawZipResourceCreator,
    ) {
        process(args, id, content, resourceCreator)
    }
}