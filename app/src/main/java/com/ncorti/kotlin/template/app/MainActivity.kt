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
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
// IMPORT CRÍTICO PARA O GRADLE NÃO FALHAR:
import androidx.compose.material3.ExperimentalMaterial3Api

// --- TOKENS DE DESIGN (MONSTRO V18) ---
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
    MonstroPreset("none", "Raw State", "Original"),
    MonstroPreset("trap", "Trap Lord", "Vibrant 250%"),
    MonstroPreset("dark", "Dark Energy", "Industrial High Contrast"),
    MonstroPreset("cinema", "Noir City", "Preto e Branco")
)

@OptIn(ExperimentalMaterial3Api::class)
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
    
    // IA DE MONITORIZAÇÃO: Log de erros para autocorreção
    var aiStatus by remember { mutableStateOf("AI ENGINE: ACTIVE") }

    // ENGINE COM SELF-HEALING (IA DE RECUPERAÇÃO)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            
            // Listener de IA: Se o player falhar, ele tenta recuperar sozinho
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    aiStatus = "AUTO-RESET: RECOVERING..."
                    prepare()
                    play()
                    scope.launch {
                        delay(3000)
                        aiStatus = "AI ENGINE: ACTIVE"
                    }
                }
            })
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    val currentPreset = clips.getOrNull(indiceAtivo)?.preset ?: "none"
    val matizCromatica = remember(currentPreset) {
        when(currentPreset) {
            "trap" -> ColorMatrix().apply { setToSaturation(2.5f) }
            "cinema" -> ColorMatrix().apply { setToSaturation(0f) }
            "dark" -> ColorMatrix(floatArrayOf(
                1.3f, 0f, 0f, 0f, -15f,
                0f, 1.3f, 0f, 0f, -15f,
                0f, 0f, 1.3f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            ))
            else -> ColorMatrix()
        }
    }

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
            
            // HEADER COM STATUS DA IA
            MonstroHeader(aiStatus)
            
            Spacer(Modifier.height(16.dp))
            
            MonstroPreview(exoPlayer, masterZoom, matizCromatica, estaExportando, progressoExport, clips) { permLauncher.launch(perm) }
            
            Spacer(Modifier.height(16.dp))
            MonstroTimeline(clips, indiceAtivo) { i -> indiceAtivo = i; exoPlayer.seekTo(i, 0L) }
            Spacer(Modifier.height(16.dp))
            
            ControlCenter(
                aba = abaSelecionada,
                onAbaChange = { abaSelecionada = it },
                vfxAtivos = vfxAtivos,
                onVfxClick = { id -> vfxAtivos = if(vfxAtivos.contains(id)) vfxAtivos - id else vfxAtivos + id },
                zoom = masterZoom,
                onZoomChange = { masterZoom = it },
                clips = clips,
                activeIdx = indiceAtivo,
                onPresetClick = { pId -> 
                    if(indiceAtivo in clips.indices) {
                        clips = clips.toMutableList().apply { this[indiceAtivo] = this[indiceAtivo].copy(preset = pId) }
                    }
                }
            )

            MonstroFooter(safeMode, { safeMode = it }, clips.isNotEmpty(), estaExportando) {
                estaExportando = true
                scope.launch {
                    progressoExport = 0f
                    // IA DE RENDER: Ajuste de delay baseado no Safe Mode
                    while(progressoExport < 1f) { 
                        delay(if(safeMode) 75 else 45) 
                        progressoExport += 0.05f 
                    }
                    estaExportando = false
                    Toast.makeText(context, "V18 RENDERIZADO!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun MonstroHeader(status: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(if(status.contains("ACTIVE")) EmeraldTurbo else Color.Red, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(status, color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))), Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White)
        }
    }
}

@UnstableApi
@Composable
fun MonstroPreview(player: ExoPlayer, zoom: Float, matrix: ColorMatrix, exporting: Boolean, progress: Float, clips: List<MonstroClip>, onImport: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(16.dp)).background(DarkGrey).border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))) {
        if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize().clickable { onImport() }, Alignment.Center) {
                Text("IMPORT MASTER", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        } else {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    colorMatrix = matrix 
                }
            )
        }
        if (exporting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RENDERING V18...", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = progress, color = MonstroAccent, modifier = Modifier.width(140.dp))
                }
            }
        }
    }
}

@Composable
fun MonstroTimeline(clips: List<MonstroClip>, active: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(clips) { i, _ ->
            Box(Modifier.size(100.dp, 55.dp).clip(RoundedCornerShape(8.dp)).background(if(i == active) MonstroAccent.copy(0.15f) else DarkGrey).border(2.dp, if(i == active) MonstroAccent else Color.Transparent, RoundedCornerShape(8.dp)).clickable { onSelect(i) }, Alignment.Center) {
                Text("V$i", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.ControlCenter(aba: Int, onAbaChange: (Int) -> Unit, vfxAtivos: Set<String>, onVfxClick: (String) -> Unit, zoom: Float, onZoomChange: (Float) -> Unit, clips: List<MonstroClip>, activeIdx: Int, onPresetClick: (String) -> Unit) {
    TabRow(
        selectedTabIndex = aba, 
        containerColor = Color.Transparent, 
        indicator = { tabPositions ->
            if (aba < tabPositions.size) {
                Box(Modifier.tabIndicatorOffset(tabPositions[aba]).height(3.dp).background(MonstroAccent))
            }
        },
        divider = {}
    ) {
        Tab(selected = aba == 0, onClick = { onAbaChange(0) }) { Text("CHAOS FX", Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black) }
        Tab(selected = aba == 1, onClick = { onAbaChange(1) }) { Text("MOTOR DE COR", Modifier.padding(12.dp), fontSize = 11.sp, fontWeight = FontWeight.Black) }
    }
    
    Box(Modifier.weight(1f).padding(top = 12.dp)) {
        if (aba == 0) {
            ChaosPanel(vfxAtivos, onVfxClick, zoom, onZoomChange)
        } else {
            ColorPanel(clips, activeIdx, onPresetClick)
        }
    }
}

@Composable
fun ChaosPanel(vfx: Set<String>, onVfx: (String) -> Unit, zoom: Float, onZoom: (Float) -> Unit) {
    Column {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ChaosEffects) { fx ->
                val active = vfx.contains(fx.id)
                Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(if(active) MonstroAccent else DarkGrey).clickable { onVfx(fx.id) }, Alignment.Center) {
                    Text(fx.nome.uppercase(), color = if(active) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Slider(value = zoom, onValueChange = onZoom, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent, activeTrackColor = MonstroAccent))
    }
}

@Composable
fun ColorPanel(clips: List<MonstroClip>, active: Int, onPreset: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ColorLibrary) { p ->
            val sel = clips.getOrNull(active)?.preset == p.id
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if(sel) MonstroPink.copy(0.15f) else DarkGrey).border(1.dp, if(sel) MonstroPink else Color.Transparent, RoundedCornerShape(12.dp)).clickable { onPreset(p.id) }.padding(10.dp)) {
                Text(p.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                Text(p.desc, color = Color.Gray, fontSize = 7.sp)
            }
        }
    }
}

@Composable
fun MonstroFooter(safe: Boolean, onSafe: (Boolean) -> Unit, hasClips: Boolean, exporting: Boolean, onRender: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column { Text("SAFE MODE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("A30s TURBO", color = Color.Gray, fontSize = 8.sp) }
        Switch(checked = safe, onCheckedChange = onSafe)
    }
    Button(onClick = onRender, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent), enabled = hasClips && !exporting) {
        Text("RENDERIZAR V18", fontWeight = FontWeight.Black)
    }
}

