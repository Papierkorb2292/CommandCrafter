package net.papierkorb2292.command_crafter.test

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.NullSerializer
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.databind.ser.std.ToStringSerializerBase
import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import net.minecraft.block.Block
import net.minecraft.fluid.Fluid
import net.minecraft.item.Item
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer
import net.minecraft.test.TestContext
import net.minecraft.text.Text
import net.minecraft.world.World
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.mixin.test.TestContextAccessor
import org.apache.logging.log4j.core.pattern.AnsiEscape
import java.nio.file.Path
import javax.swing.text.html.parser.Entity

object TestSnapshotHelper {
    val projectDirectory = Path.of("").toAbsolutePath().parent.parent // Current directory is CommandCrafter/build/gametest/
    val testDirectory = projectDirectory.resolve("__snapshots__")

    val simpleModule = SimpleModule()
        .addSerializer(IntList::class.java, IntList.JacksonSerializer)
        .addSerializer(CommandDispatcher::class.java, NullSerializer.instance)
        .addSerializer(MinecraftServer::class.java, NullSerializer.instance)
        .addSerializer(World::class.java, ToStringSerializer.instance)
        .addSerializer(Registry::class.java, ToStringSerializer.instance)
        .addSerializer(RegistryEntry::class.java, ToStringSerializer.instance)
        .addSerializer(CommandNode::class.java, MappedToStringSerializer<CommandNode<*>> { it.name })
        .addSerializer(Entity::class.java, ToStringSerializer.instance)
        .addSerializer(Block::class.java, ToStringSerializer.instance)
        .addSerializer(Fluid::class.java, ToStringSerializer.instance)
        .addSerializer(Item::class.java, ToStringSerializer.instance)
    val objectMapper = ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .registerModule(simpleModule)

    val ansiNormalSequence = AnsiEscape.createSequence("normal")
    val ansiInsertSequence = AnsiEscape.createSequence("green")
    val ansiDeleteSequence = AnsiEscape.createSequence("red")

    val diffSeparatorString = " | "

    val diffRowGenerator = DiffRowGenerator.create()
        .showInlineDiffs(true)
        .lineNormalizer { it } // Default is for HTML, but the output is supposed to be written to console, not in HTML, so no modifications are necessary
        .oldTag { tag, start -> // Default is for HTML formatting, but the output is supposed to be formatted in ANSI for console
            when(tag) {
                DiffRow.Tag.INSERT -> if(start) ansiInsertSequence else ansiNormalSequence
                DiffRow.Tag.DELETE, DiffRow.Tag.CHANGE -> if(start) ansiDeleteSequence else ansiNormalSequence
                DiffRow.Tag.EQUAL -> ""
            }
        }
        .newTag { tag, start ->
            when(tag) {
                DiffRow.Tag.INSERT, DiffRow.Tag.CHANGE -> if(start) ansiInsertSequence else ansiNormalSequence
                DiffRow.Tag.DELETE -> if(start) ansiDeleteSequence else ansiNormalSequence
                DiffRow.Tag.EQUAL -> ""
            }
        }
        .build()


    fun TestContext.assertEqualsSnapshot(value: Any?, idSuffix: String) {
        assertEqualsSnapshot(value, Text.literal(idSuffix), idSuffix)
    }

    fun TestContext.assertEqualsSnapshot(value: Any?, message: Text, idSuffix: String? = null) {
        val testId = (this as TestContextAccessor).test.instanceEntry.registryKey()
        val fileSuffix = if(idSuffix == null) "" else "_$idSuffix"
        val fileExtension = ".json"
        val fileName = testId.value.namespace + "_" + testId.value.path + fileSuffix + fileExtension
        val file = testDirectory.resolve(fileName).toFile()
        val jsonValue = try {
            objectMapper.writeValueAsString(value)
        } catch(e: JsonMappingException) {
            throw createError("Failed to serialize snapshot: ${e.message}")
        }
        if(!file.exists()) {
            CommandCrafter.LOGGER.warn(Text.translatable("Found no snapshot file for test '%s', creating new snapshot...", fileName).string)
            file.parentFile.mkdirs()
            file.createNewFile()
            file.writeText(jsonValue)
            return
        }
        val snapshot = file.readText()
        val errorText = Text.translatable("Test '%s' (%s) did not match snapshot, see diff.", fileName, message)
        if(snapshot != jsonValue) {
            val diff = diffRowGenerator.generateDiffRows(snapshot.lines(), jsonValue.lines())
            printDiffRows(diff, errorText)
        }
        assertTrue(snapshot == jsonValue, errorText)
    }

    private const val EXPECTED_HEADER = "expected (snapshot)"
    private const val ACTUAL_HEADER = "actual (parameter)"

    /**
     * Formats and prints a side by side view of the old and new strings
     */
    fun printDiffRows(diff: List<DiffRow>, message: Text) {
        val diffWithHeader = diff.toMutableList()
        // Add header followed by empty line to separate it from the rest
        diffWithHeader.add(0, DiffRow(DiffRow.Tag.CHANGE, EXPECTED_HEADER, ACTUAL_HEADER))
        diffWithHeader.add(1, DiffRow(DiffRow.Tag.CHANGE, "", ""))
        val oldLineLengths = diffWithHeader.map { it to getStringCharLength(it.oldLine) }
        val maxLineLength = oldLineLengths.maxOfOrNull { it.second } ?: return
        val formatted = oldLineLengths.joinToString("\n") { (diff, oldLineLength) ->
            val padding = " ".repeat(maxLineLength - oldLineLength)
            diff.oldLine + padding + diffSeparatorString + diff.newLine
        }
        // Print message and the formatted diff. A normal ansi formatting is inserted between them so
        // that the coloring for `LOGGER.error` only affects the message but not the diff, which has
        // its own coloring
        CommandCrafter.LOGGER.error(message.string + "\n" + ansiNormalSequence + formatted)
    }

    /**
     * Counts how many columns the string is assumed to take up when printed to the console.
     * This means not counting any ansi escapes.
     */
    fun getStringCharLength(string: String): Int {
        var count = 0
        var i = 0
        var isSkippingAnsi = false
        while(i < string.length) {
            if(isSkippingAnsi) {
                if(string.startsWith(AnsiEscape.SUFFIX.code, i)) {
                    isSkippingAnsi = false
                }
                i++
                continue
            }
            if(string.startsWith(AnsiEscape.CSI.code, i)) {
                isSkippingAnsi = true
                i++
                continue
            }
            i++
            count++
        }
        return count
    }

    private class MappedToStringSerializer<T>(val mapper: (T) -> Any): ToStringSerializerBase(Any::class.java) {
        override fun valueToString(value: Any?): String {
            @Suppress("UNCHECKED_CAST")
            return mapper(value as T).toString()
        }
    }
}