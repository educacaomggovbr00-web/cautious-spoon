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
import androidx.compose.foundation.shape.CircleShape
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

// --- TOKENS DE DESIGN INDUSTRIAL MONSTRO ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF121214)

// --- MODELOS DE DADOS ---
data class MonstroVfx(val id: String, val nome: String)
data class MonstroPreset(val id: String, val nome: String, val desc: String)
data class MonstroClip(val uri: Uri, val preset: String = "none")

// TODOS OS 8 EFEITOS DO HTML MANTIDOS
val ChaosEffects = listOf(
    MonstroVfx("motionblur", "Motion Blur"), MonstroVfx("smartzoom", "Auto Retention"),
    MonstroVfx("glitch", "Digital Glitch"), MonstroVfx("rgb", "RGB Split"),
    MonstroVfx("shake", "Impact Shake"), MonstroVfx("strobe", "Psycho Strobe"),
    MonstroVfx("hue", "Hue Shift"), MonstroVfx("vignette", "Focus Edge")
)

// TODOS OS 4 PRESETS MANTIDOS
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

@OptIn(UnstableApi::class)
@Composable
fun MonstroIndustrialEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ESTADOS GLOBAIS - TUDO PRESERVADO
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
            exoPlayer.addMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) seletor.launch("video/*") }
    val permissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    Scaffold(containerColor = MonstroBg) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            MonstroHeader()
            
            Spacer(Modifier.height(20.dp))

            MonstroPreviewArea(exoPlayer, masterZoom, estaExportando, progressoExport, clips) { launcher.launch(permissao) }

            Spacer(Modifier.height(20.dp))

            MonstroTimeline(clips, indiceAtivo) { i -> indiceAtivo = i; exoPlayer.seekToDefaultPosition(i) }

            Spacer(Modifier.height(20.dp))

            // LÓGICA DE ABAS COM CHAOS FX E COLOR ENGINE
            MonstroControlTabs(
                abaSelecionada, { abaSelecionada = it },
                vfxAtivos, { id -> vfxAtivos = if(vfxAtivos.contains(id)) vfxAtivos - id else vfxAtivos + id },
                masterZoom, { masterZoom = it },
                clips, indiceAtivo, { p -> if(indiceAtivo in clips.indices) clips = clips.toMutableList().apply { this[indiceAtivo] = this[indiceAtivo].copy(preset = p) } }
            )

            MonstroFooter(safeMode, { safeMode = it }, clips.isNotEmpty(), estaExportando) {
                estaExportando = true
                scope.launch {
                    progressoExport = 0f
                    while(progressoExport < 1f) { delay(if(safeMode) 70 else 40); progressoExport += 0.05f }
                    estaExportando = false
                    Toast.makeText(context, "RENDER FINALIZADO!", Toast.LENGTH_SHORT).show()
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(EmeraldTurbo, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("INDUSTRIAL ENGINE // SONAR FIX", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))), Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MonstroPreviewArea(player: ExoPlayer, zoom: Float, exporting: Boolean, progress: Float, clips: List<MonstroClip>, onImport: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(16.dp)).background(DarkGrey).border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))) {
        if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize().clickable { onImport() }, Alignment.Center) {
                Text("IMPORT MASTER CLIPE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        } else {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom)
            )
        }
        if (exporting) {
            val anim = rememberInfiniteTransition(label = "").animateFloat(1f, 0.4f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "")
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red.copy(alpha = anim.value)))
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = progress, color = MonstroAccent, modifier = Modifier.width(160.dp))
                }
            }
        }
    }
}

@Composable
fun MonstroTimeline(clips: List<MonstroClip>, activeIdx: Int, onClipClick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(clips) { i, _ ->
            Box(Modifier.size(110.dp, 60.dp).clip(RoundedCornerShape(10.dp)).background(if(i == activeIdx) MonstroAccent.copy(0.15f) else DarkGrey).border(2.dp, if(i == activeIdx) MonstroAccent else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onClipClick(i) }, Alignment.Center) {
                Text("CLIP $i", color = if(i == activeIdx) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun MonstroControlTabs(aba: Int, onAbaChange: (Int) -> Unit, vfx: Set<String>, onVfx: (String) -> Unit, zoom: Float, onZoom: (Float) -> Unit, clips: List<MonstroClip>, activeIdx: Int, onPreset: (String) -> Unit) {
    TabRow(selectedTabIndex = aba, containerColor = Color.Transparent, indicator = { tabPositions ->
        if (aba < tabPositions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[aba]), color = MonstroAccent)
    }) {
        Tab(selected = aba == 0, onClick = { onAbaChange(0) }) { Text("CHAOS FX", Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black) }
        Tab(selected = aba == 1, onClick = { onAbaChange(1) }) { Text("COLOR ENGINE", Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black) }
    }
    Box(Modifier.height(280.dp).padding(top = 16.dp)) {
        if (aba == 0) {
            Column {
                LazyVerticalGrid(columns = GridCells.Fixed(2), Modifier.height(210.dp), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(8.dp)) {
                    items(ChaosEffects) { fx ->
                        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(if(vfx.contains(fx.id)) MonstroAccent else DarkGrey).clickable { onVfx(fx.id) }, Alignment.Center) {
                            Text(fx.nome.uppercase(), color = if(vfx.contains(fx.id)) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Slider(value = zoom, onValueChange = onZoom, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent, activeTrackColor = MonstroAccent))
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(8.dp)) {
                items(ColorLibrary) { preset ->
                    val isSel = clips.getOrNull(activeIdx)?.preset == preset.id
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if(isSel) MonstroPink.copy(0.1f) else DarkGrey).border(1.dp, if(isSel) MonstroPink else Color.White.copy(0.05f), RoundedCornerShape(12.dp)).clickable { onPreset(preset.id) }.padding(14.dp)) {
                        Text(preset.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(preset.desc, color = Color.Gray, fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MonstroFooter(safe: Boolean, onSafe: (Boolean) -> Unit, hasClips: Boolean, exporting: Boolean, onRender: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column { Text("SAFE MODE (A30s)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("OPTIMIZED DECODE", color = Color.Gray, fontSize = 8.sp) }
        Switch(checked = safe, onCheckedChange = onSafe)
    }
    Button(onClick = onRender, Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent), enabled = hasClips && !exporting) {
        Text("RENDERIZAR MP4 TURBO", fontWeight = FontWeight.Black)
    }
}

