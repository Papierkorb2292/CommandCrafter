package net.papierkorb2292.command_crafter.test

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.gametest.v1.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import java.nio.file.Path
import kotlin.math.sqrt

data class BenchmarkResult(
    val name: String,
    val averageTime: Double,
    val stdDeviation: Double,
    val iterations: Int
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

    @GameTest
    fun benchmarkMacroExhaustiveCompletions(context: GameTestHelper) {
        val lines = listOf("$$() ")
        val analyzingResult = TestCommandCrafter.analyseCommand(context, lines)
        benchmark("Test Macro Completions", 500, 500) {
            analyzingResult.getCompletions(5, null)!!.get()
        }
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