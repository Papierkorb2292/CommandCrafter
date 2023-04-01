package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.papierkorb2292.command_crafter.parser.DatapackBuildArgs.DatapackBuildArgsArgumentType.ARG_ALREADY_SPECIFIED_EXCEPTION
import java.util.concurrent.CompletableFuture

class DatapackBuildArgs(val keepDirectives: Boolean = false, val permissionLevel: Int? = null) {
    companion object {
        private val ARGUMENTS: MutableMap<String, BuildArg> = HashMap()

        fun registerArgument(name: String, argument: BuildArg) {
            ARGUMENTS[name] = argument
        }

        init {
            registerArgument("keepDirectives", object : BuildArg {
                override val nameSuggestion: String
                    get() = "keepDirectives"

                override fun parse(reader: StringReader, builder: DatapackBuildArgsBuilder) {
                    if(builder.keepDirectives) {
                        throw ARG_ALREADY_SPECIFIED_EXCEPTION.createWithContext(reader, "keepDirectives")
                    }
                    builder.keepDirectives = true
                }

                override fun suggest(reader: StringReader, prefix: StringBuilder, builder: SuggestionsBuilder): Boolean {
                    return if (!reader.canRead()) {
                        builder.suggest(prefix.toString())
                        true
                    } else false
                }
            })
            registerArgument("functionPermissionLevel", object : BuildArg {
                override val nameSuggestion: String
                    get() = "functionPermissionLevel="

                override fun parse(reader: StringReader, builder: DatapackBuildArgsBuilder) {
                    if(builder.permissionLevel != null) {
                        throw ARG_ALREADY_SPECIFIED_EXCEPTION.createWithContext(reader, "permissionLevel")
                    }
                    reader.skipWhitespace()
                    reader.expect('=')
                    reader.skipWhitespace()
                    builder.permissionLevel = reader.readInt()
                }

                override fun suggest(reader: StringReader, prefix: StringBuilder, builder: SuggestionsBuilder): Boolean {
                    reader.skipWhitespace()
                    if(!reader.canRead() || reader.read() != '=') {
                        builder.suggest(prefix.toString().plus('='))
                        return true
                    }
                    val start = reader.cursor
                    while(reader.canRead() && StringReader.isAllowedNumber(reader.peek())) {
                        reader.skip()
                    }
                    if(start == reader.cursor) {
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerExpectedInt().createWithContext(reader)
                    }
                    prefix.append('=').append(reader.string.substring(start, reader.cursor))
                    return false
                }

            })
        }
    }

    object DatapackBuildArgsArgumentType: ArgumentType<DatapackBuildArgsBuilder> {
        private val EXAMPLES = listOf("keepDirectives", "functionPermissionLevel=2", "keepDirectives functionPermissionLevel = 3")
        private val INVALID_ARG_NAME_EXCEPTION = SimpleCommandExceptionType { "Error while parsing datapack build args: Encountered invalid or empty argument name" }

        val ARG_ALREADY_SPECIFIED_EXCEPTION = DynamicCommandExceptionType { Text.of("Error while parsing datapack build args: Argument $it is specified multiple times") }

        override fun parse(reader: StringReader): DatapackBuildArgsBuilder {
            val builder = DatapackBuildArgsBuilder()
            while(reader.canRead() && reader.peek() != '\n') {
                reader.skipWhitespace()
                val name = reader.readUnquotedString()
                (ARGUMENTS[name] ?: throw INVALID_ARG_NAME_EXCEPTION.createWithContext(reader)).parse(reader, builder)
                if(reader.canRead()) {
                    reader.expect(' ')
                }
            }
            return builder
        }

        override fun getExamples() = EXAMPLES

        override fun <S> listSuggestions(
            context: CommandContext<S>,
            builder: SuggestionsBuilder,
        ): CompletableFuture<Suggestions> {
            val prefix = StringBuilder()
            val reader = StringReader(builder.remaining)
            val arguments = HashMap(ARGUMENTS)
            while(!reader.canRead() || reader.peek( ) != '\n') {
                if(reader.cursor != 0) {
                    if(reader.canRead()) reader.skip()
                    prefix.append(' ')
                }
                val name = reader.readUnquotedString()
                val completedArg = arguments[name]
                if (completedArg == null) {
                    for((argName, arg) in arguments) {
                        if(argName.startsWith(name)) {
                            builder.suggest(prefix.toString() + arg.nameSuggestion)
                        }
                    }
                    break
                }
                arguments.remove(name)
                prefix.append(name)
                if(completedArg.suggest(reader, prefix, builder)) {
                    break
                }
            }
            return builder.buildFuture()
        }

        fun getArgsBuilder(context: CommandContext<ServerCommandSource>, name: String): DatapackBuildArgsBuilder = context.getArgument(name, DatapackBuildArgsBuilder::class.java)
    }

    class DatapackBuildArgsBuilder {
        var keepDirectives = false
        var permissionLevel: Int? = null

        fun build(): DatapackBuildArgs {
            return DatapackBuildArgs(keepDirectives, permissionLevel)
        }
    }

    interface BuildArg {
        val nameSuggestion: String
        fun parse(reader: StringReader, builder: DatapackBuildArgsBuilder)
        fun suggest(reader: StringReader, prefix: StringBuilder, builder: SuggestionsBuilder): Boolean
    }
}