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
import androidx.annotation.OptIn
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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

// --- TOKENS ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF121214)

// --- DATA ---
data class MonstroVfx(val id: String, val nome: String)
data class MonstroPreset(val id: String, val nome: String, val desc: String)
data class MonstroClip(val uri: Uri, val preset: String = "none")

val ChaosEffects = listOf(
    MonstroVfx("motionblur", "Motion Blur"),
    MonstroVfx("smartzoom", "Auto Retention"),
    MonstroVfx("glitch", "Digital Glitch"),
    MonstroVfx("rgb", "RGB Split"),
    MonstroVfx("shake", "Impact Shake"),
    MonstroVfx("strobe", "Psycho Strobe"),
    MonstroVfx("hue", "Hue Shift"),
    MonstroVfx("vignette", "Focus Edge")
)

val ColorLibrary = listOf(
    MonstroPreset("none", "Raw State", "Pipeline sem filtros"),
    MonstroPreset("trap", "Trap Lord", "Saturação Turbo 220%"),
    MonstroPreset("dark", "Dark Energy", "Contraste Industrial 180%"),
    MonstroPreset("cinema", "Blockbuster", "Orange & Teal Look")
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

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MonstroIndustrialEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            clips = clips + MonstroClip(it)
            exoPlayer.addMediaItem(MediaItem.fromUri(it)); exoPlayer.prepare(); exoPlayer.play()
        }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) launcher.launch("video/*") }
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    Scaffold(containerColor = MonstroBg) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            // HEADER
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text("UNIVERSAL BUILD // NO ERRORS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))), Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))

            // PREVIEW
            Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(16.dp)).background(DarkGrey).border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))) {
                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxSize().clickable { permLauncher.launch(perm) }, Alignment.Center) {
                        Text("IMPORT MASTER", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = exoPlayer; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } }, modifier = Modifier.fillMaxSize())
                }
                if (estaExportando) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), Alignment.Center) {
                        Text("RENDERIZANDO...", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // TIMELINE
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(clips) { i, _ ->
                    Box(Modifier.size(100.dp, 55.dp).clip(RoundedCornerShape(8.dp)).background(if(i == indiceAtivo) MonstroAccent.copy(0.15f) else DarkGrey).border(2.dp, if(i == indiceAtivo) MonstroAccent else Color.Transparent, RoundedCornerShape(8.dp)).clickable { indiceAtivo = i; exoPlayer.seekToDefaultPosition(i) }, Alignment.Center) {
                        Text("V$i", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ABAS COM INDICADOR MANUAL (INFALÍVEL)
            TabRow(
                selectedTabIndex = abaSelecionada,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    if (abaSelecionada < tabPositions.size) {
                        // AQUI ESTÁ A CORREÇÃO MÁGICA: CRIAMOS O INDICADOR NA MÃO
                        Box(
                            Modifier
                                .tabIndicatorOffset(tabPositions[abaSelecionada])
                                .height(3.dp)
                                .background(MonstroAccent)
                        )
                    }
                },
                divider = {}
            ) {
                Tab(selected = abaSelecionada == 0, onClick = { abaSelecionada = 0 }) { Text("CHAOS FX", Modifier.padding(12.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
                Tab(selected = abaSelecionada == 1, onClick = { abaSelecionada = 1 }) { Text("COLOR ENGINE", Modifier.padding(12.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }

            // PAINEL
            Box(Modifier.weight(1f).padding(top = 12.dp)) {
                if (abaSelecionada == 0) {
                    ChaosPanel(vfxAtivos, masterZoom, { vfxAtivos = it }, { masterZoom = it })
                } else {
                    ColorPanel(clips, indiceAtivo) { newList -> clips = newList }
                }
            }

            // FOOTER
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("SAFE MODE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Switch(checked = safeMode, onCheckedChange = { safeMode = it })
            }
            Button(onClick = {
                estaExportando = true
                scope.launch {
                    progressoExport = 0f
                    while(progressoExport < 1f) { delay(if(safeMode) 70 else 40); progressoExport += 0.1f }
                    estaExportando = false
                    Toast.makeText(context, "PRONTO!", Toast.LENGTH_SHORT).show()
                }
            }, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent), enabled = clips.isNotEmpty() && !estaExportando) {
                Text("RENDERIZAR", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun ChaosPanel(vfx: Set<String>, zoom: Float, onVfx: (Set<String>) -> Unit, onZoom: (Float) -> Unit) {
    Column {
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ChaosEffects) { fx ->
                val active = vfx.contains(fx.id)
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(if(active) MonstroAccent else DarkGrey).clickable { onVfx(if(active) vfx - fx.id else vfx + fx.id) }, Alignment.Center) {
                    Text(fx.nome.uppercase(), color = if(active) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Slider(value = zoom, onValueChange = onZoom, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent, activeTrackColor = MonstroAccent))
    }
}

@Composable
fun ColorPanel(clips: List<MonstroClip>, idx: Int, onUpdate: (List<MonstroClip>) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ColorLibrary) { p ->
            val sel = clips.getOrNull(idx)?.preset == p.id
            Box(Modifier.height(50.dp).clip(RoundedCornerShape(8.dp)).background(if(sel) MonstroPink.copy(0.2f) else DarkGrey).border(1.dp, if(sel) MonstroPink else Color.Transparent, RoundedCornerShape(8.dp)).clickable {
                if(idx in clips.indices) { val l = clips.toMutableList(); l[idx] = l[idx].copy(preset = p.id); onUpdate(l) }
            }, Alignment.Center) {
                Text(p.nome, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
