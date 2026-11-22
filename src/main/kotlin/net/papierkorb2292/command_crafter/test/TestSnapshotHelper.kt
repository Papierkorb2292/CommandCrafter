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
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.github.difflib.text.DiffRow
import com.github.difflib.text.DiffRowGenerator
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import net.fabricmc.fabric.impl.permission.CommandPermissionContext
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.Fluid
import net.papierkorb2292.command_crafter.CommandCrafter
import net.papierkorb2292.command_crafter.editor.processing.SemanticTokensBuilder
import net.papierkorb2292.command_crafter.helper.IntList
import net.papierkorb2292.command_crafter.mixin.test.GameTestHelperAccessor
import org.apache.logging.log4j.core.pattern.AnsiEscape
import javax.swing.text.html.parser.Entity

object TestSnapshotHelper {
    val simpleModule = SimpleModule()
        .addSerializer(IntList::class.java, IntList.JacksonSerializer)
        .addSerializer(SemanticTokensBuilder::class.java, SemanticTokensBuilder.PrettyJacksonSerializer)
        .addSerializer(CommandDispatcher::class.java, NullSerializer.instance)
        .addSerializer(MinecraftServer::class.java, NullSerializer.instance)
        .addSerializer(Level::class.java, ToStringSerializer.instance)
        .addSerializer(Registry::class.java, ToStringSerializer.instance)
        .addSerializer(Holder::class.java, ToStringSerializer.instance)
        .addSerializer(CommandNode::class.java, MappedToStringSerializer<CommandNode<*>> { it.name })
        .addSerializer(Entity::class.java, ToStringSerializer.instance)
        .addSerializer(Block::class.java, ToStringSerializer.instance)
        .addSerializer(Fluid::class.java, ToStringSerializer.instance)
        .addSerializer(Item::class.java, ToStringSerializer.instance)
        .addSerializer(CommandPermissionContext::class.java, NullSerializer.instance)
    val objectMapper = ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
        .registerModule(simpleModule)
        .registerModule(Jdk8Module())

    val ansiNormalSequence = AnsiEscape.createSequence("normal")
    val ansiInsertSequence = AnsiEscape.createSequence("green")
    val ansiDeleteSequence = AnsiEscape.createSequence("red")

    val diffSeparatorString = " | "
    private const val LINE_WRAP_MARKER = "↩"

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


    fun GameTestHelper.assertEqualsSnapshot(value: Any?, idSuffix: String) {
        assertEqualsSnapshot(value, Component.literal(idSuffix), idSuffix)
    }

    fun GameTestHelper.assertEqualsSnapshot(value: Any?, message: Component, idSuffix: String? = null) {
        val testId = (this as GameTestHelperAccessor).testInfo.testHolder.key()
        val fileSuffix = if(idSuffix == null) "" else "_$idSuffix"
        val fileExtension = ".json"
        val fileName = testId.identifier().namespace + "_" + testId.identifier().path + fileSuffix + fileExtension
        val snapshotFile = TestCommandCrafter.snapshotDirectory.resolve(fileName).toFile()
        val updatedSnapshotFile = TestCommandCrafter.updatedSnapshotDirectory.resolve(fileName).toFile()
        val jsonValue = try {
            objectMapper.writeValueAsString(value)
        } catch(e: JsonMappingException) {
            throw assertionException("Failed to serialize snapshot: ${e.message}")
        }
        updatedSnapshotFile.parentFile.mkdirs()
        updatedSnapshotFile.writeText(jsonValue) // Always write the updated snapshot, so it can be easily diffed
        if(!snapshotFile.exists()) {
            CommandCrafter.LOGGER.warn(Component.translatable("Found no snapshot file for test '%s', creating new snapshot...", fileName).string)
            snapshotFile.parentFile.mkdirs()
            snapshotFile.createNewFile()
            snapshotFile.writeText(jsonValue)
            return
        }
        val snapshot = snapshotFile.readText()
        val errorText = Component.translatable(
            "Test '%s' (%s) did not match snapshot, see diff with `git diff %s`.",
            fileName,
            message,
            TestCommandCrafter.projectDirectory.relativize(snapshotFile.toPath())
        )
        assertTrue(snapshot == jsonValue, errorText)
    }

    private const val EXPECTED_HEADER = "expected (snapshot)"
    private const val ACTUAL_HEADER = "actual (parameter)"

    /**
     * Formats and prints a side by side view of the old and new strings
     */
    fun printDiffRows(diff: List<DiffRow>, message: Component) {
        val diffWithHeader = diff.toMutableList()
        // Add header followed by empty line to separate it from the rest
        diffWithHeader.add(0, DiffRow(DiffRow.Tag.CHANGE, EXPECTED_HEADER, ACTUAL_HEADER))
        diffWithHeader.add(1, DiffRow(DiffRow.Tag.CHANGE, "", ""))
        val formatted = formatDiffRows(diffWithHeader, getTerminalWidth())
        // Print message and the formatted diff. A normal ansi formatting is inserted between them so
        // that the coloring for `LOGGER.error` only affects the message but not the diff, which has
        // its own coloring
        CommandCrafter.LOGGER.error(message.string + "\n" + ansiNormalSequence + formatted)
    }

    /**
     * Formats a side by side diff. If a row does not fit in the available terminal width,
     * both sides are wrapped independently into fixed-width columns.
     */
    fun formatDiffRows(diff: List<DiffRow>, terminalWidth: Int): String {
        if(diff.isEmpty()) return ""

        val maxLineLength = diff.maxOf { getStringCharLength(it.oldLine) }
        val maxNewLineLength = diff.maxOf { getStringCharLength(it.newLine) }
        val separatorLength = getStringCharLength(diffSeparatorString)
        val availableWidth = maxOf(terminalWidth, separatorLength + 2)

        if(maxLineLength + separatorLength + maxNewLineLength <= availableWidth) {
            return diff.joinToString("\n") { row ->
                val oldLineLength = getStringCharLength(row.oldLine)
                val padding = " ".repeat(maxLineLength - oldLineLength)
                row.oldLine + padding + diffSeparatorString + row.newLine
            }
        }

        val columnWidth = maxOf(2, (availableWidth - separatorLength) / 2)
        val formattedRows = mutableListOf<String>()
        for(row in diff) {
            val oldChunks = wrapAnsiString(row.oldLine, columnWidth)
            val newChunks = wrapAnsiString(row.newLine, columnWidth)
            val chunkCount = maxOf(oldChunks.size, newChunks.size)
            repeat(chunkCount) { chunkIndex ->
                val oldChunk = oldChunks.getOrElse(chunkIndex) { "" }
                val newChunk = newChunks.getOrElse(chunkIndex) { "" }
                val oldPadding = " ".repeat(columnWidth - getStringCharLength(oldChunk))
                val newPadding = " ".repeat(columnWidth - getStringCharLength(newChunk))
                formattedRows.add(oldChunk + oldPadding + diffSeparatorString + newChunk + newPadding)
            }
        }
        return formattedRows.joinToString("\n")
    }

    fun getTerminalWidth(): Int =
        System.getenv("COLUMNS")?.toIntOrNull()?.takeIf { it > 0 } ?: 120

    private fun wrapAnsiString(string: String, width: Int): List<String> {
        if(width <= 0 || string.isEmpty()) return listOf(string)

        val chunks = mutableListOf<String>()
        var chunk = StringBuilder()
        var visibleLength = 0
        val contentWidth = maxOf(1, width - 1)
        var i = 0
        while(i < string.length) {
            if(string.startsWith(AnsiEscape.CSI.code, i)) {
                val end = findAnsiSequenceEnd(string, i)
                chunk.append(string, i, end)
                i = end
                continue
            }

            val codePoint = string.codePointAt(i)
            val charCount = Character.charCount(codePoint)
            if(visibleLength == contentWidth && hasVisibleTextAfter(string, i)) {
                chunks.add(chunk.toString() + ansiNormalSequence + LINE_WRAP_MARKER)
                chunk = StringBuilder()
                visibleLength = 0
            }
            chunk.appendCodePoint(codePoint)
            i += charCount
            visibleLength++
        }
        chunks.add(chunk.toString())
        return chunks
    }

    private fun findAnsiSequenceEnd(string: String, start: Int): Int {
        var i = start + AnsiEscape.CSI.code.length
        while(i < string.length) {
            val char = string[i]
            if(char.code in 0x40..0x7e) return i + 1
            i++
        }
        return string.length
    }

    private fun hasVisibleTextAfter(string: String, start: Int): Boolean {
        var i = start
        while(i < string.length) {
            if(string.startsWith(AnsiEscape.CSI.code, i)) {
                i = findAnsiSequenceEnd(string, i)
                continue
            }
            return true
        }
        return false
    }

    /**
     * Counts how many columns the string is assumed to take up when printed to the console.
     * This means not counting any ansi escapes.
     */
    fun getStringCharLength(string: String): Int {
        var count = 0
        var i = 0
        while(i < string.length) {
            if(string.startsWith(AnsiEscape.CSI.code, i)) {
                i = findAnsiSequenceEnd(string, i)
                continue
            }
            val codePoint = string.codePointAt(i)
            i += Character.charCount(codePoint)
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