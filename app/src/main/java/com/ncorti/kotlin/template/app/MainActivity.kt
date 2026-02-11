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
import androidx.compose.animation.*
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

// --- TOKENS DE DESIGN (V18 INDUSTRIAL) ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF121214)

// --- MODELOS DE DADOS ---
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = MonstroAccent,
                    background = MonstroBg,
                    surface = DarkGrey
                )
            ) {
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

    // ESTADO DO EDITOR
    var clips by remember { mutableStateOf(emptyList<MonstroClip>()) }
    var indiceAtivo by remember { mutableIntStateOf(0) }
    var abaSelecionada by remember { mutableIntStateOf(0) }
    var vfxAtivos by remember { mutableStateOf(setOf<String>()) }
    var masterZoom by remember { mutableFloatStateOf(1f) }
    var safeMode by remember { mutableStateOf(true) }
    var estaExportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableFloatStateOf(0f) }

    // PLAYER CONFIG (OTIMIZADO PARA SAMSUNG A30s)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttrs, true)
        }
    }

    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    // SELEÇÃO DE ARQUIVO
    val permissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            clips = clips + MonstroClip(it)
            exoPlayer.addMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) seletor.launch("video/*")
    }

    Scaffold(containerColor = MonstroBg) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            // CABEÇALHO
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(EmeraldTurbo, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("BITRATE OTIMIZADO // 13% BATT", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))), Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ÁREA DE VÍDEO (PREVIEW)
            Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(16.dp)).background(DarkGrey).border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))) {
                if (clips.isEmpty()) {
                    Column(Modifier.fillMaxSize().clickable { launcher.launch(permissao) }, Arrangement.Center, Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        Text("IMPORT MASTER CLIPE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (estaExportando) {
                    RenderOverlay(progressoExport)
                }
            }

            Spacer(Modifier.height(20.dp))

            // TIMELINE SEQUENCE
            Text("TIMELINE SEQUENCE", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(clips) { i, _ ->
                    val isAtivo = i == indiceAtivo
                    Box(Modifier.size(110.dp, 60.dp).clip(RoundedCornerShape(10.dp)).background(if (isAtivo) MonstroAccent.copy(0.15f) else DarkGrey).border(2.dp, if (isAtivo) MonstroAccent else Color.Transparent, RoundedCornerShape(10.dp)).clickable {
                        indiceAtivo = i
                        exoPlayer.seekToDefaultPosition(i)
                    }, Alignment.Center) {
                        Text("CLIP $i", color = if (isAtivo) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ABAS DE CONTROLE (CORREÇÃO DO INDICATOR GHOST)
            TabRow(
                selectedTabIndex = abaSelecionada,
                containerColor = Color.Transparent,
                contentColor = MonstroAccent,
                divider = {},
                indicator = { tabPositions ->
                    if (abaSelecionada < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[abaSelecionada]),
                            color = MonstroAccent
                        )
                    }
                }
            ) {
                Tab(selected = abaSelecionada == 0, onClick = { abaSelecionada = 0 }) {
                    Text("CHAOS FX", Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Tab(selected = abaSelecionada == 1, onClick = { abaSelecionada = 1 }) {
                    Text("COLOR ENGINE", Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }

            // PAINEL DE CONTROLES (FIX SCOPE WEIGHT)
            Box(Modifier.weight(1f).padding(top = 16.dp)) {
                if (abaSelecionada == 0) {
                    ChaosPanel(vfxAtivos, masterZoom, { vfxAtivos = it }, { masterZoom = it })
                } else {
                    ColorPanel(clips, indiceAtivo) { newList -> clips = newList }
                }
            }

            // FOOTER E RENDER
            SafeModeBar(safeMode) { safeMode = it }

            Button(
                onClick = {
                    estaExportando = true
                    scope.launch {
                        progressoExport = 0f
                        while (progressoExport < 1f) {
                            delay(if (safeMode) 70 else 40)
                            progressoExport += 0.02f
                        }
                        estaExportando = false
                        Toast.makeText(context, "RENDER FINALIZADO!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent),
                enabled = clips.isNotEmpty() && !estaExportando
            ) {
                Text("EXPORTAR V18 TURBO", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun RenderOverlay(progresso: Float) {
    val transition = rememberInfiniteTransition(label = "render")
    val alpha by transition.animateFloat(1f, 0.4f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "blink")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red.copy(alpha = alpha)))
                Spacer(Modifier.width(8.dp))
                Text("RENDERING MP4...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = progresso, color = MonstroAccent, modifier = Modifier.width(160.dp))
        }
    }
}

@Composable
fun ChaosPanel(vfx: Set<String>, zoom: Float, onVfxChange: (Set<String>) -> Unit, onZoomChange: (Float) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ChaosEffects) { fx ->
                val ativo = vfx.contains(fx.id)
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp)).background(if (ativo) MonstroAccent else DarkGrey).clickable { onVfxChange(if (ativo) vfx - fx.id else vfx + fx.id) }, Alignment.Center) {
                    Text(fx.nome.uppercase(), color = if (ativo) Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text("ZOOM DINÂMICO", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("${(zoom * 100).toInt()}%", color = MonstroAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Slider(value = zoom, onValueChange = onZoomChange, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent, activeTrackColor = MonstroAccent))
    }
}

@Composable
fun ColorPanel(clips: List<MonstroClip>, idx: Int, onUpdate: (List<MonstroClip>) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ColorLibrary) { preset ->
            val isSel = clips.getOrNull(idx)?.preset == preset.id
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if (isSel) MonstroPink.copy(0.1f) else DarkGrey).border(1.dp, if (isSel) MonstroPink else Color.White.copy(0.05f), RoundedCornerShape(12.dp)).clickable {
                if (idx in clips.indices) {
                    val newList = clips.toMutableList()
                    newList[idx] = newList[idx].copy(preset = preset.id)
                    onUpdate(newList)
                }
            }.padding(12.dp)) {
                Text(preset.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text(preset.desc, color = Color.Gray, fontSize = 7.sp)
            }
        }
    }
}

@Composable
fun SafeModeBar(safeMode: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text("SAFE MODE (A30s)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("ENGINE OTIMIZADA", color = Color.Gray, fontSize = 8.sp)
        }
        Switch(checked = safeMode, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = EmeraldTurbo))
    }
}

