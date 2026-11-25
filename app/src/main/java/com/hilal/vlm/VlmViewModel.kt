package com.hilal.vlm

import android.app.Application
import android.app.DownloadManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.ByteArrayOutputStream

data class VisionModel(val id: String, val name: String, val size: String, val ram: String) {
    val repo get() = "google/gemma-3n-${id}-it-litert-lm"
    val filename get() = "gemma-3n-${id}-it-int4.litertlm"
    val url get() = "https://huggingface.co/$repo/resolve/main/$filename"
}
val visionModels = listOf(
    VisionModel("E2B", "Gemma 3n E2B", "3,66 GB", "8 GB+ RAM önerilir"),
    VisionModel("E4B", "Gemma 3n E4B", "4,92 GB", "12 GB+ RAM önerilir")
)
data class ChatEntry(val question: String, val answer: String)
data class VlmState(
    val selected: String = "E2B", val ready: Set<String> = emptySet(),
    val downloading: Set<String> = emptySet(), val progress: Map<String, Float> = emptyMap(),
    val image: Bitmap? = null, val busy: Boolean = false,
    val status: String = "Bir model seçerek başlayın.", val error: String? = null,
    val messages: List<ChatEntry> = emptyList()
)

class VlmViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val downloads = app.getSystemService(DownloadManager::class.java)
    private val prefs = app.getSharedPreferences("downloads", 0)
    private val mutable = MutableStateFlow(VlmState())
    val state = mutable.asStateFlow()
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val engineLock = Mutex()
    private var engine: Engine? = null
    private var loaded: String? = null
    private val directory = File(app.getExternalFilesDir(null), "models").apply { mkdirs() }
    private fun file(model: VisionModel) = File(directory, model.filename)
    init {
        viewModelScope.launch {
            while (isActive) { refreshDownloads(); delay(700) }
        }
    }
    private fun refreshDownloads() {
        val ready = mutableSetOf<String>()
        val active = mutableSetOf<String>()
        val progress = mutableMapOf<String, Float>()
        for (model in visionModels) {
            val id = prefs.getLong(model.id, -1)
            if (prefs.getBoolean("ready_${model.id}", false) && file(model).isFile) ready += model.id
            if (id == -1L) continue
            downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (!c.moveToFirst()) { prefs.edit().remove(model.id).apply(); return@use }
                fun int(key: String) = c.getInt(c.getColumnIndexOrThrow(key))
                when (int(DownloadManager.COLUMN_STATUS)) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        prefs.edit().remove(model.id).putBoolean("ready_${model.id}", true).apply()
                        ready += model.id
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = int(DownloadManager.COLUMN_REASON)
                        downloads.remove(id)
                        prefs.edit().remove(model.id).apply()
                        mutable.update { it.copy(error = "${model.name} indirilemedi ($reason). 401/403 için model lisansını kabul edip okuma yetkili token girin. Depolama alanını ve bağlantıyı kontrol edin.") }
                    }
                    else -> {
                        active += model.id
                        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        if (total > 0) progress[model.id] = (done.toDouble() / total).toFloat()
                    }
                }
            }
        }
        mutable.update { it.copy(ready = ready, downloading = active, progress = progress) }
    }
    fun select(model: VisionModel) {
        if (state.value.busy) return
        mutable.update { it.copy(selected = model.id, messages = emptyList(), error = null) }
    }
    fun download(model: VisionModel, token: String) {
        if (model.id in state.value.downloading) return
        runCatching {
            val request = DownloadManager.Request(Uri.parse(model.url))
                .setTitle(model.name).setDescription("Görsel + metin modeli indiriliyor")
                .setDestinationUri(Uri.fromFile(file(model)))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
            if (token.isNotBlank()) request.addRequestHeader("Authorization", "Bearer ${token.trim()}")
            val id = downloads.enqueue(request)
            prefs.edit().putLong(model.id, id).apply()
            mutable.update { it.copy(downloading = it.downloading + model.id, error = null) }
        }.onFailure { e -> mutable.update { it.copy(error = "İndirme başlatılamadı: ${e.localizedMessage}") } }
    }
    fun cancel(model: VisionModel) {
        downloads.remove(prefs.getLong(model.id, -1))
        prefs.edit().remove(model.id).apply()
        refreshDownloads()
    }
    fun setImage(uri: Uri) {
        if (state.value.busy) return
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null, status = "Fotoğraf hazırlanıyor…") }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    require(options.outWidth > 0 && options.outHeight > 0) { "Görsel okunamadı." }
                    var sample = 1
                    while (maxOf(options.outWidth, options.outHeight) / sample > 1600) sample *= 2
                    app.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Görsel açılamadı.")
                }
                mutable.update { it.copy(image = bitmap, messages = emptyList(), status = "Fotoğraf hazır. Sorunuzu yazın.") }
            } catch (e: Exception) { mutable.update { it.copy(error = e.localizedMessage) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun ask(question: String) {
        val snapshot = state.value
        val image = snapshot.image ?: return
        if (snapshot.busy || question.isBlank() || snapshot.selected !in snapshot.ready) return
        val model = visionModels.first { it.id == snapshot.selected }
        mutable.update { it.copy(busy = true, error = null, status = "Model hazırlanıyor…", messages = it.messages + ChatEntry(question.trim(), "")) }
        viewModelScope.launch {
            try {
                withContext(worker) {
                    engineLock.withLock {
                        if (loaded != model.id) {
                            engine?.close()
                            engine = null
                            loaded = null
                            val config = EngineConfig(
                                modelPath = file(model).absolutePath,
                                backend = Backend.CPU(),
                                visionBackend = Backend.GPU(),
                                cacheDir = app.cacheDir.absolutePath
                            )
                            val next = Engine(config)
                            try {
                                next.initialize()
                            } catch (e: Throwable) {
                                next.close()
                                throw e
                            }
                            engine = next
                            loaded = model.id
                        }
                        mutable.update { it.copy(status = "Görsel inceleniyor…") }
                        val bytes = ByteArrayOutputStream().use { output ->
                            image.compress(Bitmap.CompressFormat.JPEG, 90, output)
                            output.toByteArray()
                        }
                        // Each request includes the image and runs with an independent context.
                        engine!!.createConversation().use { conversation ->
                            val contents = Contents.of(Content.ImageBytes(bytes), Content.Text(question.trim()))
                            conversation.sendMessageAsync(contents).collect { chunk ->
                                mutable.update { current ->
                                    val last = current.messages.last()
                                    current.copy(
                                        status = "Yanıt yazılıyor…",
                                        messages = current.messages.dropLast(1) + last.copy(answer = last.answer + chunk.toString())
                                    )
                                }
                            }
                        }
                    }
                }
                mutable.update { it.copy(status = "Yanıt tamamlandı. Yeni bir soru sorabilirsiniz.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutable.update { it.copy(error = "Model çalıştırılamadı: ${e.localizedMessage}. Yeterli RAM ve GPU desteği olan fiziksel Android cihaz kullanın.", status = "İşlem tamamlanamadı.") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() {
        // Serialize cleanup after any in-flight native inference.
        CoroutineScope(worker).launch { engineLock.withLock { engine?.close(); engine = null } }
    }
}
