@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ncorti.kotlin.template.app

/**
 * HENRIQUE, IMPORTANTE: Se o build falhar com "Unresolved reference", 
 * verifique se estas 3 linhas estão no seu build.gradle (Module: app):
 * * implementation("androidx.media3:media3-transformer:1.2.0")
 * implementation("androidx.media3:media3-effect:1.2.0")
 * implementation("androidx.media3:media3-common:1.2.0")
 */

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

// Imports básicos que não conflitam
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object MonstroTheme {
    val Bg = Color(0xFF020306)
    val Accent = Color(0xFFa855f7)
    val Pink = Color(0xFFdb2777)
    val Surface = Color(0xFF151518)
}

@Stable
data class MonstroClip(
    val uri: Uri,
    val saturation: Float = 1f
)

class EditorViewModel : ViewModel() {
    private val _clips = MutableStateFlow<List<MonstroClip>>(emptyList())
    val clips = _clips.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex = _activeIndex.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress = _exportProgress.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()

    fun addClip(uri: Uri) { _clips.value = _clips.value + MonstroClip(uri) }
    fun setActiveIndex(i: Int) { _activeIndex.value = i }

    fun renderVideo(context: Context, onComplete: (File) -> Unit, onError: (String) -> Unit) {
        if (_clips.value.isEmpty()) return
        _isExporting.value = true
        _exportProgress.value = 0f

        val outputDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Monstro")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "MONSTRO_${System.currentTimeMillis()}.mp4")

        // 1. Transformer com caminhos absolutos para ignorar erros de import
        val transformer = androidx.media3.transformer.Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : androidx.media3.transformer.Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    _isExporting.value = false
                    onComplete(outputFile)
                }
                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exception: androidx.media3.transformer.ExportException) {
                    _isExporting.value = false
                    onError(exception.message ?: "Erro Transformer")
                }
            })
            .build()

        // 2. Composição de efeitos (FQN total)
        val editedItems = _clips.value.map { clip ->
            val videoEffects = mutableListOf<androidx.media3.common.Effect>()
            
            // Forçando o uso da ColorMatrix da Media3
            val matrix = androidx.media3.common.ColorMatrix()
            matrix.setToSaturation(clip.saturation)
            videoEffects.add(androidx.media3.effect.RgbFilter.createMatrix(matrix.values))

            androidx.media3.transformer.EditedMediaItem.Builder(MediaItem.fromUri(clip.uri))
                .setEffects(androidx.media3.common.Effects(mutableListOf(), videoEffects))
                .build()
        }

        val sequence = androidx.media3.transformer.EditedMediaSequence(editedItems)
        val composition = androidx.media3.transformer.Composition.Builder(listOf(sequence)).build()

        try {
            transformer.start(composition, outputFile.path)
            
            // 3. Monitor de Progresso
            CoroutineScope(Dispatchers.Main).launch {
                val progressHolder = androidx.media3.transformer.ProgressHolder()
                while (_isExporting.value) {
                    val state = transformer.getProgress(progressHolder)
                    if (state != androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED) {
                        _exportProgress.value = progressHolder.progress.toFloat() / 100f
                    }
                    delay(250)
                }
            }
        } catch (e: Exception) {
            _isExporting.value = false
            onError(e.message ?: "Falha no Start")
        }
    }
}

@Composable
fun RealRenderEditor(viewModel: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val clips by viewModel.clips.collectAsState()
    val activeIndex by viewModel.activeIndex.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { viewModel.addClip(it) } }

    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) launcher.launch("video/*") }

    LaunchedEffect(clips) {
        exoPlayer.clearMediaItems()
        clips.forEach { exoPlayer.addMediaItem(MediaItem.fromUri(it.uri)) }
        exoPlayer.prepare()
    }

    Scaffold(
        containerColor = MonstroTheme.Bg,
        bottomBar = {
            Surface(Modifier.fillMaxWidth().height(60.dp), color = Color.Black) {
                Row(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, tint = Color.White)
                    Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxSize().clickable { permLauncher.launch(perm) }, Alignment.Center) {
                        Text("V19 - IMPORTAR VÍDEO", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = false } }, modifier = Modifier.fillMaxSize())
                }

                if (isExporting) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.9f)), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(progress = exportProgress, color = MonstroTheme.Pink)
                            Text("EXPORTANDO ${(exportProgress * 100).toInt()}%", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
            }

            LazyRow(Modifier.fillMaxWidth().height(100.dp).background(MonstroTheme.Surface), verticalAlignment = Alignment.CenterVertically) {
                itemsIndexed(clips) { i, _ ->
                    Box(Modifier.width(100.dp).height(60.dp).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(if(i == activeIndex) MonstroTheme.Accent else Color.DarkGray).clickable { viewModel.setActiveIndex(i); exoPlayer.seekTo(i, 0L) }, Alignment.Center) {
                        Text("CLIP $i", color = Color.White, fontSize = 12.sp)
                    }
                }
            }

            Button(
                onClick = { viewModel.renderVideo(context, { Toast.makeText(context, "Vídeo na Galeria!", Toast.LENGTH_LONG).show() }, { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                enabled = clips.isNotEmpty() && !isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = MonstroTheme.Pink)
            ) {
                Text("EXPORTAR MP4 REAL", fontWeight = FontWeight.Black)
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = MonstroTheme.Accent, background = MonstroTheme.Bg)) {
                RealRenderEditor()
            }
        }
    }
}

