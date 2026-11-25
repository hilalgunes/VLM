package com.hilal.vlm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB9F582), secondary = Color(0xFFBBCAB0),
                background = Color(0xFF101410), surface = Color(0xFF1B211B)
            )) { VlmScreen() }
        }
    }
}

@Composable
fun VlmScreen(vm: VlmViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var token by remember { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    val links = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::setImage)
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(insets).imePadding(),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("VLM / CİHAZ ÜZERİNDE", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text("Gör, sor, keşfet.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Fotoğraflarınız için görsel + dil modelleri. İndirdikten sonra çevrimdışı çalışır.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Text("01  Modelini seç", style = MaterialTheme.typography.titleMedium)
                Text("İndirmeler birkaç GB’tır. Wi-Fi ve yeterli boş alan önerilir. ARM64 cihaz gerekir.", style = MaterialTheme.typography.bodySmall)
            }
            items(visionModels.size) { index ->
                val model = visionModels[index]
                val selected = state.selected == model.id
                val downloading = model.id in state.downloading
                val ready = model.id in state.ready
                Card(onClick = { vm.select(model) }, enabled = !state.busy,
                    colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF293B26) else MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(model.name, fontWeight = FontWeight.Bold)
                            Text(if (selected) "● SEÇİLİ" else "VLM", color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Görsel + metin  ·  ${model.size}")
                        Text(model.ram, style = MaterialTheme.typography.bodySmall)
                        when {
                            downloading -> {
                                val progress = state.progress[model.id]
                                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("İndiriliyor ${progress?.let { "%${(it * 100).toInt()}" } ?: "…"}")
                                TextButton(onClick = { vm.cancel(model) }) { Text("İndirmeyi iptal et") }
                            }
                            ready -> Text("✓ Cihazda hazır", color = MaterialTheme.colorScheme.primary)
                            else -> Button(onClick = { vm.select(model); vm.download(model, token) }, enabled = !state.busy) { Text("Seç ve indir") }
                        }
                        TextButton(onClick = { links.openUri("https://huggingface.co/${model.repo}") }) { Text("Model ve lisans ↗") }
                    }
                }
            }
            item {
                OutlinedTextField(value = token, onValueChange = { token = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hugging Face okuma tokenı") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                Text("Önce model sayfasında Gemma lisansını kabul edin. Token yalnızca indirme için kullanılır; uygulama ayarlarına kaydedilmez.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text("02  Bir fotoğraf ekle", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                state.image?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), "Seçilen fotoğraf", Modifier.fillMaxWidth().height(230.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.busy) {
                    Text(if (state.image == null) "Galeriden fotoğraf seç" else "Fotoğrafı değiştir")
                }
            }
            item {
                Text("03  Fotoğraf hakkında sor", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = question, onValueChange = { question = it }, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    label = { Text("Sorunuz") }, placeholder = { Text("Bu fotoğrafta neler görüyorsun?") })
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.ask(question); question = "" }, modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy && state.image != null && state.selected in state.ready && question.isNotBlank()) {
                    Text(if (state.busy) "İşleniyor…" else "Fotoğrafı incele ve yanıtla")
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.error?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
            items(state.messages.size) { index ->
                val message = state.messages[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(message.question, fontWeight = FontWeight.SemiBold)
                        Text(message.answer.ifBlank { if (state.busy) "Görsel inceleniyor…" else "Yanıt alınamadı." })
                    }
                }
            }
            item { Text("Yanıtlar seçili fotoğraf ve soruya göre üretilir. Modeller hata yapabilir.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
