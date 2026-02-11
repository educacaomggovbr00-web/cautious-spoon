@file:OptIn(androidx.media3.common.util.UnstableApi::class)
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.random.Random

// --- TOKENS DE DESIGN (MONSTRO V18 TURBO) ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF0a0c12)

data class MonstroVfx(val id: String, val nome: String)
data class MonstroPreset(val id: String, val nome: String)

// Novo Data Class com suporte a Ajuste de Tempo (Trim)
data class MonstroClip(
    val id: String = Random.nextInt().toString(),
    val uri: Uri, 
    val preset: String = "none",
    val durationMs: Long = 5000L, // Mock inicial, atualizado no load
    var trimStart: Long = 0L,
    var trimEnd: Long = 5000L
)

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
    MonstroPreset("none", "Raw"),
    MonstroPreset("trap", "Trap Lord"),
    MonstroPreset("dark", "Dark Energy"),
    MonstroPreset("cinema", "Blockbuster")
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
    
    // --- ESTADOS ---
    var clips by remember { mutableStateOf(emptyList<MonstroClip>()) }
    var indiceAtivo by remember { mutableIntStateOf(0) }
    var abaSelecionada by remember { mutableIntStateOf(0) }
    var vfxAtivos by remember { mutableStateOf(setOf<String>()) }
    var masterZoom by remember { mutableFloatStateOf(1f) }
    var exportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableFloatStateOf(0f) }
    var safeMode by remember { mutableStateOf(true) }

    // --- MOTOR EXOPLAYER ---
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    // Sincronização do Player com Clips e Trim
    LaunchedEffect(clips, indiceAtivo) {
        if (indiceAtivo in clips.indices) {
            val clip = clips[indiceAtivo]
            val mediaItem = MediaItem.Builder()
                .setUri(clip.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStart)
                        .setEndPositionMs(clip.trimEnd)
                        .build()
                )
                .build()
            
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    // --- ANIMAÇÕES (HUE, SHAKE, STROBE) ---
    val infiniteTransition = rememberInfiniteTransition(label = "TurboEngine")
    val hueAnim by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "Hue")
    val shakeAnim by infiniteTransition.animateFloat(initialValue = -10f, targetValue = 10f, animationSpec = infiniteRepeatable(tween(40, easing = LinearEasing), RepeatMode.Reverse), label = "Shake")
    val strobeAnim by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 0f, animationSpec = infiniteRepeatable(tween(50, easing = LinearEasing), RepeatMode.Reverse), label = "Strobe")
    val autoZoomAnim by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.12f, animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Zoom")

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { clips = clips + MonstroClip(uri = it) }
    }

    Scaffold(containerColor = MonstroBg) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            
            // HEADER
            HeaderTurbo(safeMode)

            Spacer(Modifier.height(16.dp))
            
            // PREVIEW CANVAS
            Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(12.dp)).background(Color.Black).border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(12.dp))) {
                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxSize().clickable { launcher.launch("video/*") }, Alignment.Center) {
                        Text("IMPORT MASTER CLIP", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    }
                } else {
                    AndroidView(
                        factory = { ctx -> 
                            PlayerView(ctx).apply { 
                                this.player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT 
                            } 
                        },
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val isShake = vfxAtivos.contains("shake")
                            val isStrobe = vfxAtivos.contains("strobe")
                            val isAutoZoom = vfxAtivos.contains("smartzoom")
                            val isHue = vfxAtivos.contains("hue")

                            translationX = if(isShake) shakeAnim else 0f
                            alpha = if(isStrobe && strobeAnim < 0.5f) 0.15f else 1f
                            scaleX = masterZoom * (if(isAutoZoom) autoZoomAnim else 1f)
                            scaleY = masterZoom * (if(isAutoZoom) autoZoomAnim else 1f)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val currentPreset = clips.getOrNull(indiceAtivo)?.preset ?: "none"
                                val matrix = ColorMatrix()
                                when(currentPreset) {
                                    "trap" -> matrix.apply { setToSaturation(2.2f) }
                                    "dark" -> matrix.apply { setToSaturation(0.8f); rotateRed(10f) }
                                    "cinema" -> matrix.apply { setToSaturation(1.1f) }
                                }
                                if (isHue) matrix.rotateBlue(hueAnim)
                                if (vfxAtivos.contains("rgb")) matrix.values[0] = 2f
                                renderEffect = android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(matrix.values)).asComposeRenderEffect()
                            }
                        }
                    )
                }
                if (vfxAtivos.contains("vignette")) {
                    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)), radius = 600f)))
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // --- TURBO TIMELINE (AS LINHAS DE VÍDEO QUE PEDISTE) ---
            Text("TURBO TIMELINE", color = Color.White.copy(0.4f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            
            Box(Modifier.fillMaxWidth().height(100.dp).background(DarkGrey, RoundedCornerShape(8.dp)).border(0.5.dp, Color.White.copy(0.05f), RoundedCornerShape(8.dp))) {
                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("NENHUMA TRACK ATIVA", color = Color.DarkGray, fontSize = 8.sp)
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(clips) { i, clip ->
                            VideoTrackItem(
                                clip = clip,
                                isSelected = i == indiceAtivo,
                                onSelect = { indiceAtivo = i },
                                onTrimChange = { start, end ->
                                    clips = clips.toMutableList().apply {
                                        this[i] = this[i].copy(trimStart = start, trimEnd = end)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            // CONTROL CENTER
            ControlCenter(
                aba = abaSelecionada,
                onAbaChange = { abaSelecionada = it },
                vfxAtivos = vfxAtivos,
                onVfxToggle = { id -> vfxAtivos = if(vfxAtivos.contains(id)) vfxAtivos - id else vfxAtivos + id },
                zoom = masterZoom,
                onZoomChange = { masterZoom = it },
                safeMode = safeMode,
                onSafeToggle = { safeMode = !safeMode },
                currentPresetId = clips.getOrNull(indiceAtivo)?.preset ?: "none",
                onPresetSelect = { pId ->
                    if (indiceAtivo in clips.indices) {
                        clips = clips.toMutableList().apply { this[indiceAtivo] = this[indiceAtivo].copy(preset = pId) }
                    }
                }
            )

            // RENDER
            Button(
                onClick = { /* Export Logic Sim */ exportando = true; scope.launch { delay(2000); exportando = false; Toast.makeText(context, "SALVO!", Toast.LENGTH_SHORT).show() } },
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent),
                enabled = clips.isNotEmpty()
            ) {
                Text("RENDER MASTER MP4", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun HeaderTurbo(safeMode: Boolean) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MonstroAccent), Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(if(safeMode) "A30s OPTIMIZED" else "MAX POWER", color = EmeraldTurbo, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VideoTrackItem(
    clip: MonstroClip,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTrimChange: (Long, Long) -> Unit
) {
    // Cálculo de largura visual baseado no tempo (simplificado)
    val width = ((clip.trimEnd - clip.trimStart) / 10L).coerceAtLeast(80L).dp
    
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) MonstroAccent.copy(0.3f) else Color.White.copy(0.05f))
            .border(1.5.dp, if (isSelected) MonstroAccent else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { onSelect() }
            .padding(4.dp)
    ) {
        Text("TRACK ID: ${clip.id.take(4)}", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        
        if (isSelected) {
            // "Alças" de Ajuste (Slider de Trim Simplificado)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Box(Modifier.size(12.dp).background(MonstroPink, CircleShape)) // Handle Start
                Box(Modifier.size(12.dp).background(MonstroPink, CircleShape)) // Handle End
            }
            Text("${clip.trimStart}ms - ${clip.trimEnd}ms", color = MonstroPink, fontSize = 6.sp, fontWeight = FontWeight.Bold)
        } else {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(0.2f)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCenter(aba: Int, onAbaChange: (Int) -> Unit, vfxAtivos: Set<String>, onVfxToggle: (String) -> Unit, zoom: Float, onZoomChange: (Float) -> Unit, safeMode: Boolean, onSafeToggle: () -> Unit, currentPresetId: String, onPresetSelect: (String) -> Unit) {
    Column {
        TabRow(
            selectedTabIndex = aba, 
            containerColor = Color.Transparent, 
            divider = {},
            indicator = { tabPositions -> Box(Modifier.tabIndicatorOffset(tabPositions[aba]).height(3.dp).background(MonstroAccent)) }
        ) {
            Tab(selected = aba == 0, onClick = { onAbaChange(0) }) { Text("CHAOS FX", Modifier.padding(10.dp), fontSize = 9.sp, fontWeight = FontWeight.Black) }
            Tab(selected = aba == 1, onClick = { onAbaChange(1) }) { Text("PRESETS", Modifier.padding(10.dp), fontSize = 9.sp, fontWeight = FontWeight.Black) }
        }
        
        Box(Modifier.height(180.dp).padding(top = 10.dp)) {
            if (aba == 0) {
                Column {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(110.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(ChaosEffects) { fx ->
                            val active = vfxAtivos.contains(fx.id)
                            Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp)).background(if(active) MonstroAccent else DarkGrey).clickable { onVfxToggle(fx.id) }, Alignment.Center) {
                                Text(fx.nome.uppercase(), color = if(active) Color.White else Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Slider(value = zoom, onValueChange = onZoomChange, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent, activeTrackColor = MonstroAccent))
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ColorLibrary) { p ->
                        val sel = currentPresetId == p.id
                        Box(Modifier.height(40.dp).clip(RoundedCornerShape(8.dp)).background(if(sel) MonstroPink.copy(0.15f) else DarkGrey).border(1.dp, if(sel) MonstroPink else Color.Transparent, RoundedCornerShape(8.dp)).clickable { onPresetSelect(p.id) }, Alignment.Center) {
                            Text(p.nome.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 8.sp)
                        }
                    }
                }
            }
        }
    }
}

