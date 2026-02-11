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
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- DESIGN TOKENS MONSTRO ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF121214)

data class MonstroVfx(val id: String, val nome: String)
data class MonstroPreset(val id: String, val nome: String, val desc: String)
data class MonstroClip(val uri: Uri, val preset: String = "none")

val ChaosEffects = listOf(
    MonstroVfx("motionblur", "Motion Blur"), MonstroVfx("smartzoom", "Auto Retention"),
    MonstroVfx("glitch", "Digital Glitch"), MonstroVfx("rgb", "RGB Split"),
    MonstroVfx("shake", "Impact Shake"), MonstroVfx("strobe", "Psycho Strobe"),
    MonstroVfx("hue", "Hue Shift"), MonstroVfx("vignette", "Focus Edge")
)

val ColorLibrary = listOf(
    MonstroPreset("none", "Raw State", "Sem filtros"),
    MonstroPreset("trap", "Trap Lord", "Saturação 220%"),
    MonstroPreset("dark", "Dark Energy", "Contraste 180%"),
    MonstroPreset("cinema", "Blockbuster", "Estilo Hollywood")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = MonstroAccent, background = MonstroBg, surface = DarkGrey)) {
                MonstroIndustrialEditor()
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MonstroIndustrialEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ESTADOS GLOBAIS
    var clips by remember { mutableStateOf(emptyList<MonstroClip>()) }
    var indiceAtivo by remember { mutableIntStateOf(0) }
    var abaSelecionada by remember { mutableIntStateOf(0) }
    var vfxAtivos by remember { mutableStateOf(setOf<String>()) }
    var masterZoom by remember { mutableFloatStateOf(1f) }
    var safeMode by remember { mutableStateOf(true) }
    var estaExportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            clips = clips + MonstroClip(it)
            exoPlayer.addMediaItem(MediaItem.fromUri(it)); exoPlayer.prepare(); exoPlayer.play()
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) seletor.launch("video/*") }
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    Scaffold(containerColor = MonstroBg) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            MonstroHeader()
            Spacer(Modifier.height(16.dp))
            MonstroPreview(exoPlayer, masterZoom, estaExportando, progressoExport, clips) { launcher.launch(perm) }
            Spacer(Modifier.height(16.dp))
            MonstroTimeline(clips, indiceAtivo) { i -> indiceAtivo = i; exoPlayer.seekToDefaultPosition(i) }
            Spacer(Modifier.height(16.dp))
            
            // PAINEL DE CONTROLE MODULARIZADO
            MonstroControls(
                abaSelecionada, { abaSelecionada = it },
                vfxAtivos, { id -> vfxAtivos = if(vfxAtivos.contains(id)) vfxAtivos - id else vfxAtivos + id },
                masterZoom, { masterZoom = it },
                clips, indiceAtivo, { p -> if(indiceAtivo in clips.indices) clips = clips.toMutableList().apply { this[indiceAtivo] = this[indiceAtivo].copy(preset = p) } }
            )

            MonstroFooter(safeMode, { safeMode = it }, clips.isNotEmpty(), estaExportando) {
                estaExportando = true
                scope.launch {
                    progressoExport = 0f
                    while(progressoExport < 1f) { delay(if(safeMode) 75 else 40); progressoExport += 0.05f }
                    estaExportando = false
                    Toast.makeText(context, "RENDERIZADO!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun MonstroHeader() {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("INDUSTRIAL // SONAR-SAFE", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))), Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
        }
    }
}

@UnstableApi
@Composable
fun MonstroPreview(player: ExoPlayer, zoom: Float, exporting: Boolean, progress: Float, clips: List<MonstroClip>, onImport: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(16.dp)).background(DarkGrey).border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))) {
        if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize().clickable { onImport() }, Alignment.Center) {
                Text("IMPORT MASTER", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        } else {
            AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } }, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom))
        }
        if (exporting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), Alignment.Center) {
                LinearProgressIndicator(progress = progress, color = MonstroAccent, modifier = Modifier.width(160.dp))
            }
        }
    }
}

@Composable
fun MonstroTimeline(clips: List<MonstroClip>, active: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(clips) { i, _ ->
            Box(Modifier.size(110.dp, 60.dp).clip(RoundedCornerShape(10.dp)).background(if(i == active) MonstroAccent.copy(0.15f) else DarkGrey).border(2.dp, if(i == active) MonstroAccent else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onSelect(i) }, Alignment.Center) {
                Text("CLIP $i", color = if(i == active) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun MonstroControls(aba: Int, onAbaChange: (Int) -> Unit, vfx: Set<String>, onVfx: (String) -> Unit, zoom: Float, onZoom: (Float) -> Unit, clips: List<MonstroClip>, active: Int, onPreset: (String) -> Unit) {
    TabRow(selectedTabIndex = aba, containerColor = Color.Transparent, indicator = { positions ->
        if (aba < positions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[aba]), color = MonstroAccent)
    }) {
        Tab(selected = aba == 0, onClick = { onAbaChange(0) }) { Text("CHAOS FX", Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black) }
        Tab(selected = aba == 1, onClick = { onAbaChange(1) }) { Text("COLOR ENGINE", Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black) }
    }
    Box(Modifier.weight(1f).padding(top = 12.dp)) {
        if (aba == 0) ChaosPanel(vfx, onVfx, zoom, onZoom) else ColorPanel(clips, active, onPreset)
    }
}

@Composable
fun ChaosPanel(vfx: Set<String>, onVfx: (String) -> Unit, zoom: Float, onZoom: (Float) -> Unit) {
    Column {
        LazyVerticalGrid(columns = GridCells.Fixed(2), Modifier.height(210.dp), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(8.dp)) {
            items(ChaosEffects) { fx ->
                val active = vfx.contains(fx.id)
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(if(active) MonstroAccent else DarkGrey).clickable { onVfx(fx.id) }, Alignment.Center) {
                    Text(fx.nome.uppercase(), color = if(active) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Slider(value = zoom, onValueChange = onZoom, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent))
    }
}

@Composable
fun ColorPanel(clips: List<MonstroClip>, active: Int, onPreset: (String) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(8.dp)) {
        items(ColorLibrary) { p ->
            val sel = clips.getOrNull(active)?.preset == p.id
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if(sel) MonstroPink.copy(0.1f) else DarkGrey).border(1.dp, if(sel) MonstroPink else Color.Transparent, RoundedCornerShape(12.dp)).clickable { onPreset(p.id) }.padding(14.dp)) {
                Text(p.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp); Text(p.desc, color = Color.Gray, fontSize = 8.sp)
            }
        }
    }
}

@Composable
fun MonstroFooter(safe: Boolean, onSafe: (Boolean) -> Unit, hasClips: Boolean, exporting: Boolean, onRender: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column { Text("SAFE MODE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("A30s OPTIMIZED", color = Color.Gray, fontSize = 8.sp) }
        Switch(checked = safe, onCheckedChange = onSafe)
    }
    Button(onClick = onRender, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent), enabled = hasClips && !exporting) {
        Text("RENDERIZAR MP4 TURBO", fontWeight = FontWeight.Black)
    }
}

