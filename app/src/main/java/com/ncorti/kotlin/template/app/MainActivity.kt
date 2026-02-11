@file:OptIn(androidx.media3.common.util.UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- TOKENS DE DESIGN ---
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
    MonstroVfx("shake", "Impact Shake"), MonstroVfx("strobe", "Psycho Strobe")
)

val ColorLibrary = listOf(
    MonstroPreset("none", "Raw State", "Original"),
    MonstroPreset("trap", "Trap Lord", "Vibrant 250%"),
    MonstroPreset("dark", "Dark Energy", "Industrial High Contrast"),
    MonstroPreset("cinema", "Noir City", "Preto e Branco")
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

@Composable
fun MonstroIndustrialEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ESTADOS DO MOTOR
    var clips by remember { mutableStateOf(emptyList<MonstroClip>()) }
    var indiceAtivo by remember { mutableIntStateOf(0) }
    var abaSelecionada by remember { mutableIntStateOf(0) }
    var masterZoom by remember { mutableFloatStateOf(1f) }
    var safeMode by remember { mutableStateOf(true) }
    var estaExportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableFloatStateOf(0f) }
    var aiStatus by remember { mutableStateOf("AI ENGINE: ACTIVE") }

    // SELF-HEALING ENGINE (IA DE RECUPERAÇÃO)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    aiStatus = "AUTO-RESET: RECOVERING..."
                    prepare(); play()
                    scope.launch { delay(2500); aiStatus = "AI ENGINE: ACTIVE" }
                }
            })
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    val currentPreset = clips.getOrNull(indiceAtivo)?.preset ?: "none"
    val matiz = remember(currentPreset) {
        when(currentPreset) {
            "trap" -> ColorMatrix().apply { setToSaturation(2.5f) }
            "cinema" -> ColorMatrix().apply { setToSaturation(0f) }
            "dark" -> ColorMatrix(floatArrayOf(1.3f,0f,0f,0f,-15f, 0f,1.3f,0f,0f,-15f, 0f,0f,1.3f,0f,-15f, 0f,0f,0f,1f,0f))
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
            
            // HEADER COM STATUS IA
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(if(aiStatus.contains("ACTIVE")) EmeraldTurbo else Color.Red, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(aiStatus, color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))), Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // PREVIEW BOX
            Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(16.dp)).background(DarkGrey)) {
                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxSize().clickable { permLauncher.launch(perm) }, Alignment.Center) {
                        Text("IMPORT MASTER", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { this.player = exoPlayer; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT } },
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = masterZoom; scaleY = masterZoom; colorMatrix = matiz 
                        }
                    )
                }
                if (estaExportando) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), Alignment.Center) {
                        LinearProgressIndicator(progress = progressoExport, color = MonstroAccent, modifier = Modifier.width(140.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // TIMELINE
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(clips) { i, _ ->
                    Box(Modifier.size(100.dp, 55.dp).clip(RoundedCornerShape(8.dp)).background(if(i == indiceAtivo) MonstroAccent.copy(0.15f) else DarkGrey).border(2.dp, if(i == indiceAtivo) MonstroAccent else Color.Transparent, RoundedCornerShape(8.dp)).clickable { indiceAtivo = i; exoPlayer.seekTo(i, 0L) }, Alignment.Center) {
                        Text("V$i", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // CONTROL CENTER
            ControlCenter(
                aba = abaSelecionada,
                onAbaChange = { abaSelecionada = it },
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

            // FOOTER / RENDER
            MonstroFooter(safeMode, { safeMode = it }, clips.isNotEmpty(), estaExportando) {
                estaExportando = true
                scope.launch {
                    progressoExport = 0f
                    while(progressoExport < 1f) { 
                        delay(if(safeMode) 70 else 40) 
                        progressoExport += 0.05f 
                    }
                    estaExportando = false
                    Toast.makeText(context, "V18 CONCLUÍDO!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun ColumnScope.ControlCenter(aba: Int, onAbaChange: (Int) -> Unit, zoom: Float, onZoomChange: (Float) -> Unit, clips: List<MonstroClip>, activeIdx: Int, onPresetClick: (String) -> Unit) {
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
        Tab(selected = aba == 0, onClick = { onAbaChange(0) }) { Text("CHAOS", Modifier.padding(12.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
        Tab(selected = aba == 1, onClick = { onAbaChange(1) }) { Text("COLORS", Modifier.padding(12.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
    }
    
    Box(Modifier.weight(1f).padding(top = 12.dp)) {
        if (aba == 0) {
            Column {
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(160.dp), Arrangement.spacedBy(8.dp)) {
                    items(ChaosEffects) { fx ->
                        Box(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(DarkGrey), Alignment.Center) {
                            Text(fx.nome.uppercase(), color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Slider(value = zoom, onValueChange = onZoomChange, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent, activeTrackColor = MonstroAccent))
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(2), Arrangement.spacedBy(8.dp)) {
                items(ColorLibrary) { p ->
                    val sel = clips.getOrNull(activeIdx)?.preset == p.id
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(if(sel) MonstroPink.copy(0.15f) else DarkGrey).border(1.dp, if(sel) MonstroPink else Color.Transparent, RoundedCornerShape(12.dp)).clickable { onPresetClick(p.id) }.padding(10.dp)) {
                        Text(p.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
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

