package net.papierkorb2292.command_crafter.parser

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.papierkorb2292.command_crafter.parser.DatapackBuildArgs.DatapackBuildArgsParser.ARG_ALREADY_SPECIFIED_EXCEPTION

class DatapackBuildArgs(val keepDirectives: Boolean = false, val permissions: PermissionSet? = null) {
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
                    if(builder.permissions != null) {
                        throw ARG_ALREADY_SPECIFIED_EXCEPTION.createWithContext(reader, "permissionLevel")
                    }
                    reader.skipWhitespace()
                    reader.expect('=')
                    reader.skipWhitespace()
                    builder.permissions = LevelBasedPermissionSet.forLevel(PermissionLevel.byId(reader.readInt()))
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

    object DatapackBuildArgsParser {
        private val INVALID_ARG_NAME_EXCEPTION = SimpleCommandExceptionType { "Error while parsing datapack build args: Encountered invalid or empty argument name" }

        val ARG_ALREADY_SPECIFIED_EXCEPTION = DynamicCommandExceptionType { Component.nullToEmpty("Error while parsing datapack build args: Argument $it is specified multiple times") }

        val SUGGESTION_PROVIDER = SuggestionProvider<CommandSourceStack> { _, builder ->
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
            builder.buildFuture()
        }

        fun parse(reader: StringReader): DatapackBuildArgsBuilder {
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
    }

    class DatapackBuildArgsBuilder {
        var keepDirectives = false
        var permissions: PermissionSet? = null

        fun build(): DatapackBuildArgs {
            return DatapackBuildArgs(keepDirectives, permissions)
        }
    }

    interface BuildArg {
        val nameSuggestion: String
        fun parse(reader: StringReader, builder: DatapackBuildArgsBuilder)
        fun suggest(reader: StringReader, prefix: StringBuilder, builder: SuggestionsBuilder): Boolean
    }
}