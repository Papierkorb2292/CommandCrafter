package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder
import net.minecraft.registry.Registry
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.function.CommandFunction
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.papierkorb2292.command_crafter.parser.helper.RawResource

object LanguageManager {
    val LANGUAGES = FabricRegistryBuilder.createSimple<(Map<String, String?>, Int) -> Language>(null, Identifier("command_crafter", "languages")).buildAndRegister()!!

    fun parseToVanilla(reader: DirectiveStringReader<RawZipResourceCreator>, source: ServerCommandSource, resource: RawResource, closure: Language.LanguageClosure) {
        val closureDepth = reader.closureDepth
        reader.enterClosure(closure)
        reader.resourceCreator.resourceStack.push(resource)
        while(reader.closureDepth != closureDepth) {
            reader.currentLanguage?.parseToVanilla(reader, source, resource)
            reader.updateLanguage()
        }
        reader.resourceCreator.resourceStack.pop()
    }

    private val UNCLOSED_SCOPE_EXCEPTION = DynamicCommandExceptionType { Text.of("Encountered unclosed scope started at line $it") }

    fun parseToCommands(reader: DirectiveStringReader<ParsedResourceCreator?>, source: ServerCommandSource, closure: Language.LanguageClosure): Array<CommandFunction.Element> {
        val closureDepth = reader.closureDepth
        val result: MutableList<CommandFunction.Element> = ArrayList()
        reader.enterClosure(closure)
        while(reader.closureDepth != closureDepth) {
            reader.currentLanguage?.run {
                result.addAll(parseToCommands(reader, source))
            }
            reader.updateLanguage()
            if(!reader.canRead() && reader.closureDepth != closureDepth) {
                throw UNCLOSED_SCOPE_EXCEPTION.create(reader.scopeStack.element().startLine)
            }
        }
        return result.toTypedArray()
    }

    init {
        Registry.register(DirectiveManager.DIRECTIVES, Identifier("language")) { reader: DirectiveStringReader<*> ->
            val language = reader.readUnquotedString()
            reader.switchLanguage(
                requireNotNull(LANGUAGES.get(Identifier(language))) { "Error while parsing function: Encountered unknown language '$language' on line ${reader.currentLine}" }
                    .run {
                        reader.skipWhitespace()
                        if(!reader.canRead() || reader.peek() != '(') {
                            return@run invoke(emptyMap(), reader.currentLine)
                        }
                        reader.skip()
                        val args: MutableMap<String, String?> = HashMap()
                        while(reader.canRead()) {
                            reader.skipWhitespace()
                            if(!reader.canRead()) {
                                break
                            }
                            if(reader.peek() == ')') {
                                reader.skip()
                                return@run invoke(args, reader.currentLine)
                            }
                            val parameter = reader.readUnquotedString()
                            require(parameter.isNotEmpty()) { "Error while parsing language: Expected parameter on line ${reader.currentLine}" }
                            reader.skipWhitespace()
                            args[parameter] = if(reader.peek() == ',' || reader.peek() == ')') {
                                null
                            } else {
                                reader.expect('=')
                                reader.skipWhitespace()

                                reader.readUnquotedString().apply {
                                    require(isNotEmpty()) { "Error while parsing language: Expected argument for parameter $parameter on line ${reader.currentLine}" }
                                }
                            }
                        }
                        throw IllegalArgumentException("Error while parsing language: Found no closing parentheses for language parameters on line ${reader.currentLine}")
                    }
            )
        }
    }
}