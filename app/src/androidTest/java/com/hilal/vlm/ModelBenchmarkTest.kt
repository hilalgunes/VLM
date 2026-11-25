package com.hilal.vlm

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Opt-in real-device experiment; consumes the same fixture bytes as the native runner. */
@RunWith(AndroidJUnit4::class)
class ModelBenchmarkTest {
    @Test
    fun compareGemma() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit benchmark flag is required", args.getString("benchmark") == "true")
        val modelId = args.getString("model", "E2B")!!
        require(modelId in setOf("E2B", "E4B"))
        val repeats = args.getString("repeats", "3")!!.toInt().also { require(it in 1..10) }
        val root = File(app.getExternalFilesDir(null), "benchmark").apply { mkdirs() }
        val cases = JSONArray(File(root, "cases.json").readText())
        val output = File(root, "gemma-$modelId.jsonl")
        output.writeText("")
        val model = File(app.getExternalFilesDir(null), "models/gemma-3n-$modelId-it-int4.litertlm")
        check(model.isFile) { "Model indirilmemiş: ${model.name}" }
        val loadStart = SystemClock.elapsedRealtimeNanos()
        Engine(EngineConfig(modelPath = model.absolutePath, backend = Backend.CPU(threadCount = 4),
            visionBackend = Backend.GPU(), maxNumTokens = 4096, cacheDir = app.cacheDir.absolutePath)).use { engine ->
            engine.initialize()
            val loadMs = (SystemClock.elapsedRealtimeNanos() - loadStart) / 1e6
            File(root, "phase.txt").writeText("loaded")
            // One untimed warm-up; all reported requests have a loaded engine and fresh conversation.
            engine.createConversation(ConversationConfig(maxOutputToken = 16,
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 42))).use { c ->
                c.sendMessage(Contents.of(Content.ImageFile(File(root, "shapes.png").absolutePath), Content.Text("How many blue circles? Reply with only the number.")))
            }
            for (repeat in 1..repeats) {
                for (index in 0 until cases.length()) {
                    val case = cases.getJSONObject(index)
                    File(root, "phase.txt").writeText("${case.getString("id")}:$repeat")
                    val running = AtomicBoolean(true)
                    val peakRss = AtomicLong(0)
                    val sampler = thread(name = "benchmark-rss") {
                        while (running.get()) {
                            val rss = File("/proc/self/status").useLines { lines ->
                                lines.firstOrNull { it.startsWith("VmRSS:") }?.trim()?.split(Regex("\\s+"))?.get(1)?.toLong() ?: 0L
                            }
                            peakRss.updateAndGet { maxOf(it, rss) }
                            Thread.sleep(200)
                        }
                    }
                    val started = SystemClock.elapsedRealtimeNanos()
                    val cpuStart = Process.getElapsedCpuTime()
                    var firstMs: Double? = null
                    val text = StringBuilder()
                    var error: String? = null
                    try {
                        engine.createConversation(ConversationConfig(maxOutputToken = 64,
                            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 42))).use { conversation ->
                            conversation.sendMessageAsync(Contents.of(
                                Content.ImageFile(File(root, case.getString("image")).absolutePath),
                                Content.Text(case.getString("prompt")))).collect { chunk ->
                                val value = chunk.toString()
                                if (value.isNotEmpty() && firstMs == null) firstMs = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
                                text.append(value)
                            }
                        }
                    } catch (e: Exception) { error = e.toString() }
                    val totalMs = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
                    val cpuMs = Process.getElapsedCpuTime() - cpuStart
                    running.set(false)
                    sampler.join()
                    val mem = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    val result = JSONObject().put("model", "gemma-3n-$modelId")
                        .put("runtime", "LiteRT-LM 0.18.1 CPU + GPU vision")
                        .put("case", case.getString("id")).put("repeat", repeat)
                        .put("load_ms", loadMs).put("first_token_ms", firstMs ?: JSONObject.NULL)
                        .put("total_ms", totalMs).put("cpu_ms", cpuMs)
                        .put("peak_rss_kib", peakRss.get()).put("after_pss_kib", mem.totalPss)
                        .put("answer", text.toString()).put("error", error ?: JSONObject.NULL)
                    output.appendText(result.toString()+"\n")
                    File(root, "phase.txt").writeText("cooldown")
                    Thread.sleep(3000)
                }
            }
        }
        File(root, "phase.txt").writeText("complete")
    }
}
