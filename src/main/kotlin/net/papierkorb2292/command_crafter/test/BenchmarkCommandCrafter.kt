package net.papierkorb2292.command_crafter.test

import com.mojang.brigadier.context.StringRange
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.commands.Commands
import net.minecraft.commands.ParseContext
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.phys.Vec3
import net.papierkorb2292.command_crafter.editor.debugger.helper.withExtension
import net.papierkorb2292.command_crafter.editor.debugger.server.functions.FunctionDebugHandler.Companion.FUNCTION_FILE_EXTENSTION
import net.papierkorb2292.command_crafter.mixin.test.ServerFunctionLibraryAccessor
import net.papierkorb2292.command_crafter.mixin.test.ServerFunctionManagerAccessor
import net.papierkorb2292.command_crafter.parser.DirectiveStringReader
import net.papierkorb2292.command_crafter.parser.FileMappingInfo.Companion.fromLines
import net.papierkorb2292.command_crafter.parser.LanguageManager.DEFAULT_CLOSURE
import net.papierkorb2292.command_crafter.parser.LanguageManager.parseToCommands
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator
import net.papierkorb2292.command_crafter.parser.ParsedResourceCreator.ResourceStackInfo
import net.papierkorb2292.command_crafter.parser.helper.FileSourceContainer
import java.nio.file.Path
import java.util.*
import kotlin.math.sqrt

data class BenchmarkResult(
    val name: String,
    val averageTime: Double,
    val stdDeviation: Double,
    val iterations: Int,
)

object BenchmarkCommandCrafter {
    val projectDirectory = Path.of("").toAbsolutePath().parent.parent // Current directory is CommandCrafter/build/gametest/
    val benchmarkResults = mutableListOf<BenchmarkResult>()

    //@GameTest
    fun benchmarkMacros(context: GameTestHelper) {
        val lines = $$"""
            $execute $(this_is_slow)run dialog show @a {type:"notice",title:"Test",action:{action:{\
                type: "dynamic/run_command"\
            },label:"ok"}}
        """.trimIndent().lines()
        benchmark("Test Macros", 500, 500) {
            TestCommandCrafter.analyseCommand(context, lines)
        }
        context.succeed()
    }

    //@GameTest
    fun benchmarkMacroExhaustiveCompletions(context: GameTestHelper) {
        val lines = listOf("$$() ")
        val analyzingResult = TestCommandCrafter.analyseCommand(context, lines)
        benchmark("Test Macro Completions", 500, 500) {
            analyzingResult.getCompletions(5, null)!!.get()
        }
        context.succeed()
    }

    //@GameTest
    fun benchmarkUnicodeCompletions(context: GameTestHelper) {
        val lines = listOf("tellraw @a \"\\N{}\"")
        val analyzingResult = TestCommandCrafter.analyseCommand(context, lines)
        benchmark("Test Unicode Completions", 10, 10) {
            analyzingResult.getCompletions(15, null)!!.get()
        }
        context.succeed()
    }

    @GameTest
    fun benchmarkDebuggerStandby(context: GameTestHelper) {
        val markers = context.spawn(EntityTypes.MARKER, Vec3.ZERO, 1000)
        markers.forEach { marker -> marker.addTag("debuggerTest") }
        val functionId = Identifier.withDefaultNamespace("debugger_standby_benchmark")
        val lines = Collections.nCopies(10, "execute as @e[tag=debuggerTest] at @e[tag=debuggerTest] run tp @s ~ ~ ~")

        buildAndRegisterDebuggableFunction(functionId, lines, context)

        val functionCommandString = "function $functionId"
        val source = context.level.server.createCommandSourceStack().withSuppressedOutput()
        val functionCommand = context.level.server.commands.parseBox().parse(functionCommandString, ParseContext.of(source))

        benchmark("Test Debugger", 500, 500) {
            Commands.performCommand(source, functionCommand, functionCommandString)
        }

        markers.forEach(context::kill)
        context.succeed()
    }

    inline fun benchmark(name: String, warmup: Int, repetitions: Int, runner: () -> Unit) {
        val times = mutableListOf<Long>()

        repeat(warmup) { runner() }

        repeat(repetitions) {
            val startTime = System.nanoTime()
            runner()
            val endTime = System.nanoTime()
            times.add(endTime - startTime)
        }

        val average = times.average()
        val variance = times.map { (it - average) * (it - average) }.average()
        val stdDev = sqrt(variance)

        benchmarkResults.add(BenchmarkResult(name, average, stdDev, repetitions))
    }

    private fun buildAndRegisterDebuggableFunction(id: Identifier, lines: List<String>, context: GameTestHelper) {
        val source = context.level.server.createCommandSourceStack().withSuppressedOutput()

        val parserProvider = TestCommandCrafter.getParserProvider(context)
        val resourceCreator = ParsedResourceCreator(id, "test", parserProvider)
        val infoSetCallbacks = ArrayList<(ResourceStackInfo) -> Unit>()
        resourceCreator.originResourceIdSetEventStack.push({ idSetter ->
            idSetter.invoke(id)
        })
        resourceCreator.originResourceInfoSetEventStack.push({ infoSetter ->
            infoSetCallbacks.add(infoSetter)
        })
        @Suppress("UNCHECKED_CAST")
        val reader = DirectiveStringReader(
            fromLines(lines),
            resourceCreator
        )
        val startCursor = reader.absoluteCursor
        val functionBuilder = parseToCommands(reader, DEFAULT_CLOSURE)

        for(callback in infoSetCallbacks) {
            callback.invoke(ResourceStackInfo(id, StringRange(startCursor, reader.absoluteCursor)))
        }

        val function = functionBuilder.build(id, parserProvider)

        if(function is FileSourceContainer) {
            function.`command_crafter$setFileSource`(lines, id.withExtension(FUNCTION_FILE_EXTENSTION))
        }

        val library = (context.level.server.functions as ServerFunctionManagerAccessor)
            .getLibrary() as ServerFunctionLibraryAccessor
        library.functions = library.functions + mapOf(id to function)
    }

    init {
        ServerLifecycleEvents.SERVER_STOPPED.register {
            if (benchmarkResults.isNotEmpty()) {
                println("========== Benchmark Results ==========")
                benchmarkResults.forEach { result ->
                    println("${result.name}:")
                    println("  Iterations: ${result.iterations}")
                    println("  Average Time: ${String.format("%.4f", result.averageTime / 1_000_000)} ms")
                    println("  Std Deviation: ${String.format("%.4f", result.stdDeviation / 1_000_000)} ms")
                }
                println("========================================")
            }
        }
    }
}