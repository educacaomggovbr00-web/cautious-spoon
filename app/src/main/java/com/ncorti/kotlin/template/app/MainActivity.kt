@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.ncorti.kotlin.template.app

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

// --- RESOLUÇÃO DEFINITIVA DE AMBIGUIDADE (ALIASES) ---
import androidx.media3.common.ColorMatrix as Media3ColorMatrix
import androidx.media3.common.Effect
import androidx.media3.common.Effects
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.RgbFilter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition as Media3Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// --- MOTOR DE DESIGN ---
object MonstroTheme {
    val Bg = Color(0xFF020306)
    val Accent = Color(0xFFa855f7)
    val Pink = Color(0xFFdb2777)
    val Surface = Color(0xFF151518)
}

@Stable
data class MonstroClip(
    val uri: Uri,
    val saturation: Float = 1.0f
)

// --- VIEWMODEL BLINDADA PARA O BUILD ---
class EditorViewModel : ViewModel() {
    private val _clips = MutableStateFlow<List<MonstroClip>>(emptyList())
    val clips = _clips.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex = _activeIndex.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress = _exportProgress.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()

    fun addClip(uri: Uri) {
        _clips.value = _clips.value + MonstroClip(uri)
    }

    fun setActiveIndex(i: Int) { _activeIndex.value = i }

    fun renderVideo(context: Context, onComplete: (File) -> Unit, onError: (String) -> Unit) {
        if (_clips.value.isEmpty()) return
        _isExporting.value = true
        _exportProgress.value = 0f

        val outputDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Monstro")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "MONSTRO_${System.currentTimeMillis()}.mp4")

        // 1. Criar o Transformer com o Listener usando Media3Composition (Alias)
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Media3Composition, exportResult: ExportResult) {
                    _isExporting.value = false
                    onComplete(outputFile)
                }
                override fun onError(composition: Media3Composition, exportResult: ExportResult, exportException: ExportException) {
                    _isExporting.value = false
                    onError(exportException.message ?: "Erro de Codec")
                }
            })
            .build()

        // 2. Construir a sequência de clips usando tipos explícitos
        val editedItems = _clips.value.map { clip ->
            val videoEffects = mutableListOf<Effect>()
            
            // Usando Media3ColorMatrix para evitar conflito com Compose Graphics
            val matrix = Media3ColorMatrix()
            matrix.setToSaturation(clip.saturation)
            videoEffects.add(RgbFilter.createMatrix(matrix.values))

            EditedMediaItem.Builder(MediaItem.fromUri(clip.uri))
                .setEffects(Effects(emptyList<Effect>(), videoEffects))
                .build()
        }

        // 3. Composição final usando o alias para não confundir o compilador
        val composition = Media3Composition.Builder(listOf(EditedMediaSequence(editedItems)))
            .build()

        try {
            transformer.start(composition, outputFile.path)
        } catch (e: Exception) {
            _isExporting.value = false
            onError(e.message ?: "Falha ao iniciar render")
            return
        }

        // 4. Monitoramento do progresso real
        CoroutineScope(Dispatchers.Main).launch {
            val progressHolder = ProgressHolder()
            while (_isExporting.value) {
                val state = transformer.getProgress(progressHolder)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    _exportProgress.value = progressHolder.progress.toFloat() / 100f
                }
                delay(200)
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

@Composable
fun RealRenderEditor(viewModel: EditorViewModel = viewModel()) {
    val context = LocalContext.current
    val clips by viewModel.clips.collectAsState()
    val activeIndex by viewModel.activeIndex.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()

    val exoPlayer = remember(context) { ExoPlayer.Builder(context).build() }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.addClip(it) }
    }

    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
        Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { 
        if(it) launcher.launch("video/*") 
    }

    LaunchedEffect(clips) {
        exoPlayer.clearMediaItems()
        clips.forEach { exoPlayer.addMediaItem(MediaItem.fromUri(it.uri)) }
        exoPlayer.prepare()
    }

    Scaffold(
        containerColor = MonstroTheme.Bg,
        bottomBar = { BottomControls() }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            
            // PREVIEW AREA
            Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxSize().clickable { permLauncher.launch(perm) }, Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Text("IMPORTAR CLIPS", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                } else {
                    AndroidView(
                        factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // EXPORT OVERLAY
                if (isExporting) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.9f)), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(progress = exportProgress, color = MonstroTheme.Pink, strokeWidth = 8.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("CONSTRUINDO MP4...", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${(exportProgress * 100).toInt()}%", color = MonstroTheme.Pink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // TIMELINE
            LazyRow(
                Modifier.fillMaxWidth().height(110.dp).background(MonstroTheme.Surface),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                itemsIndexed(clips) { i, _ ->
                    Box(
                        Modifier.width(100.dp).height(60.dp).padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if(i == activeIndex) MonstroTheme.Accent else Color.DarkGray)
                            .clickable { viewModel.setActiveIndex(i); exoPlayer.seekTo(i, 0L) },
                        Alignment.Center
                    ) {
                        Text("CLIP $i", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ACTION BUTTON
            Button(
                onClick = {
                    viewModel.renderVideo(context, 
                        onComplete = { Toast.makeText(context, "Vídeo Salvo em Movies/Monstro", Toast.LENGTH_LONG).show() },
                        onError = { Toast.makeText(context, "Erro: $it", Toast.LENGTH_SHORT).show() }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(80.dp).padding(16.dp),
                enabled = clips.isNotEmpty() && !isExporting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MonstroTheme.Pink)
            ) {
                Icon(Icons.Default.MovieFilter, null)
                Spacer(Modifier.width(8.dp))
                Text("EXPORTAR V19 FINAL", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun BottomControls() {
    Surface(Modifier.fillMaxWidth().height(60.dp), color = Color.Black) {
        Row(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            IconButton(onClick = {}) { Icon(Icons.Default.Tune, null, tint = Color.White) }
            IconButton(onClick = {}) { Icon(Icons.Default.Speed, null, tint = Color.White) }
            IconButton(onClick = {}) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White) }
        }
    }
}
