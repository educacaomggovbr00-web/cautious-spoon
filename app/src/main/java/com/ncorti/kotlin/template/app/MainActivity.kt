Package com.ncorti.kotlin.template.app

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
import androidx.compose.ui.graphics.ColorMatrix
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

// --- TOKENS DE DESIGN INDUSTRIAL ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF121214)

data class MonstroVfx(val id: String, val nome: String)
data class MonstroPreset(val id: String, val nome: String, val matrix: ColorMatrix)
data class MonstroClip(val uri: Uri, val presetId: String = "raw")

val ChaosEffects = listOf(
    MonstroVfx("motionblur", "Motion Blur"), MonstroVfx("smartzoom", "Auto Retention"),
    MonstroVfx("glitch", "Digital Glitch"), MonstroVfx("rgb", "RGB Split"),
    MonstroVfx("shake", "Impact Shake"), MonstroVfx("strobe", "Psycho Strobe"),
    MonstroVfx("hue", "Hue Shift"), MonstroVfx("vignette", "Focus Edge")
)

// PRESETS COM MATRIZ DE COR REAIS
val ColorLibrary = listOf(
    MonstroPreset("raw", "Raw State", ColorMatrix()),
    MonstroPreset("trap", "Trap Lord", ColorMatrix().apply { setToSaturation(2.2f) }),
    MonstroPreset("dark", "Dark Energy", ColorMatrix().apply { 
        val m = floatArrayOf(
            1.5f, 0f, 0f, 0f, -50f,
            0f, 1.5f, 0f, 0f, -50f,
            0f, 0f, 1.5f, 0f, -50f,
            0f, 0f, 0f, 1f, 0f
        )
        set(ColorMatrix(m))
    }),
    MonstroPreset("cinema", "Blockbuster", ColorMatrix().apply { setToSaturation(1.2f) })
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
    
    // ESTADOS
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
            
            // PREVIEW COM EFEITOS REAIS (ColorMatrix + Zoom)
            val currentPreset = ColorLibrary.find { it.id == clips.getOrNull(indiceAtivo)?.presetId } ?: ColorLibrary[0]
            
            MonstroPreview(exoPlayer, masterZoom, currentPreset.matrix, estaExportando, progressoExport, clips) { launcher.launch(perm) }
            
            Spacer(Modifier.height(16.dp))
            MonstroTimeline(clips, indiceAtivo) { i -> indiceAtivo = i; exoPlayer.seekToDefaultPosition(i) }
            Spacer(Modifier.height(16.dp))
            
            // ABAS
            TabRow(selectedTabIndex = abaSelecionada, containerColor = Color.Transparent, indicator = { positions ->
                if (abaSelecionada < positions.size) TabRowDefaults.Indicator(Modifier.tabIndicatorOffset(positions[abaSelecionada]), color = MonstroAccent)
            }) {
                Tab(selected = abaSelecionada == 0, onClick = { abaSelecionada = 0 }) { Text("CHAOS FX", Modifier.padding(12.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
                Tab(selected = abaSelecionada == 1, onClick = { abaSelecionada = 1 }) { Text("PRESETS", Modifier.padding(12.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
            }

            Box(Modifier.weight(1f).padding(top = 12.dp)) {
                if (abaSelecionada == 0) {
                    ChaosControl(vfxAtivos, { id -> vfxAtivos = if(vfxAtivos.contains(id)) vfxAtivos - id else vfxAtivos + id }, masterZoom, { masterZoom = it })
                } else {
                    PresetControl(clips, indiceAtivo) { id -> 
                        if(indiceAtivo in clips.indices) clips = clips.toMutableList().apply { this[indiceAtivo] = this[indiceAtivo].copy(presetId = id) }
                    }
                }
            }

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
            Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("INDUSTRIAL // FX-ENGINE", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))), Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@UnstableApi
@Composable
fun MonstroPreview(player: ExoPlayer, zoom: Float, matrix: ColorMatrix, exporting: Boolean, progress: Float, clips: List<MonstroClip>, onImport: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(12.dp)).background(DarkGrey).border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(12.dp))) {
        if (clips.isEmpty()) {
            Box(Modifier.fillMaxSize().clickable { onImport() }, Alignment.Center) {
                Text("IMPORT MASTER", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        } else {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } }, 
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoom, 
                        scaleY = zoom,
                        colorMatrix = matrix // APLICA O PRESET DE COR AQUI
                    )
            )
        }
        if (exporting) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = progress, color = MonstroAccent, modifier = Modifier.width(120.dp))
                }
            }
        }
    }
}

@Composable
fun MonstroTimeline(clips: List<MonstroClip>, active: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(clips) { i, _ ->
            Box(Modifier.size(100.dp, 50.dp).clip(RoundedCornerShape(8.dp)).background(if(i == active) MonstroAccent.copy(0.1f) else DarkGrey).border(1.5.dp, if(i == active) MonstroAccent else Color.Transparent, RoundedCornerShape(8.dp)).clickable { onSelect(i) }, Alignment.Center) {
                Text("V$i", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun ChaosControl(vfx: Set<String>, onVfx: (String) -> Unit, zoom: Float, onZoom: (Float) -> Unit) {
    Column {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), 
            modifier = Modifier.height(180.dp), 
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ChaosEffects) { fx ->
                val active = vfx.contains(fx.id)
                Box(Modifier.fillMaxWidth().height(45.dp).clip(RoundedCornerShape(8.dp)).background(if(active) MonstroAccent else DarkGrey).clickable { onVfx(fx.id) }, Alignment.Center) {
                    Text(fx.nome.uppercase(), color = if(active) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Slider(value = zoom, onValueChange = onZoom, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent))
    }
}

@Composable
fun PresetControl(clips: List<MonstroClip>, active: Int, onPreset: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ColorLibrary) { p ->
            val sel = clips.getOrNull(active)?.presetId == p.id
            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(if(sel) MonstroPink.copy(0.1f) else DarkGrey).border(1.dp, if(sel) MonstroPink else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onPreset(p.id) }.padding(10.dp)) {
                Text(p.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Text("ENGINE ACTIVE", color = Color.Gray, fontSize = 7.sp)
            }
        }
    }
}

@Composable
fun MonstroFooter(safe: Boolean, onSafe: (Boolean) -> Unit, hasClips: Boolean, exporting: Boolean, onRender: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text("SAFE MODE (A30s)", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Switch(checked = safe, onCheckedChange = onSafe)
    }
    Button(onClick = onRender, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent), enabled = hasClips && !exporting) {
        Text("RENDER TURBO", fontWeight = FontWeight.Black)
    }
}
