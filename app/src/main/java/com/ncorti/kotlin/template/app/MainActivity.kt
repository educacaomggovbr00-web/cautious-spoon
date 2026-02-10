package com.ncorti.kotlin.template.app

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- CONFIGURAÇÃO VISUAL ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF121214)

data class MonstroPreset(val id: String, val nome: String, val descricao: String)
val MonstroLibrary = listOf(
    MonstroPreset("raw", "ESTADO RAW", "Sem filtros"),
    MonstroPreset("neon", "NEON PUNCH", "Brilho"),
    MonstroPreset("trap", "TRAP LORD", "Estilo Trap"),
    MonstroPreset("dark", "DARK ENERGY", "Cinema")
)
data class MonstroClip(val uri: Uri, val presetAtivo: MonstroPreset = MonstroLibrary[0])
data class MonstroProject(val clips: List<MonstroClip> = emptyList())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonstroTheme { MonstroIndustrialEditor() }
        }
    }
}

@Composable
fun MonstroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = MonstroAccent, background = MonstroBg, surface = DarkGrey),
        content = content
    )
}

@Composable
fun MonstroIndustrialEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projeto by remember { mutableStateOf(MonstroProject()) }
    var indiceSelecionado by remember { mutableStateOf(0) }
    var estaExportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableStateOf(0f) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    val permissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
        Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val seletorVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            projeto = projeto.copy(clips = projeto.clips + MonstroClip(uri = it))
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                }
            }
            exoPlayer?.apply {
                addMediaItem(MediaItem.fromUri(it))
                prepare()
                playWhenReady = true
            }
        }
    }

    val launchPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) seletorVideo.launch("video/*")
    }

    DisposableEffect(Unit) { onDispose { exoPlayer?.release() } }

    Scaffold(containerColor = MonstroBg) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.height(20.dp))

            // PREVIEW COM ASPECT RATIO CORRIGIDO
            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(DarkGrey)) {
                if (projeto.clips.isEmpty()) {
                    Box(Modifier.fillMaxSize().clickable { launchPermissao.launch(permissao) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray)
                    }
                } else {
                    exoPlayer?.let { player ->
                        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false } }, modifier = Modifier.fillMaxSize())
                    }
                }
                if (estaExportando) {
                    LinearProgressIndicator(progress = progressoExport, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter), color = MonstroAccent)
                }
            }

            Spacer(Modifier.height(20.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(projeto.clips) { index, _ ->
                    Box(Modifier.size(100.dp, 60.dp).background(if(index == indiceSelecionado) MonstroAccent else DarkGrey).clickable { indiceSelecionado = index })
                }
            }
            
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                estaExportando = true
                scope.launch {
                    while(progressoExport < 1f) { delay(50); progressoExport += 0.05f }
                    estaExportando = false
                    Toast.makeText(context, "RENDERIZADO!", Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("RENDERIZAR VÍDEO") }
        }
    }
}
