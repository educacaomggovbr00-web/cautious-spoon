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

// --- DESIGN SYSTEM MONSTRO ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF121214)

data class MonstroVfx(val id: String, val nome: String)
data class MonstroPreset(val id: String, val nome: String, val desc: String)
data class MonstroClip(val uri: Uri, val preset: String = "none")

val ChaosEffects = listOf(
    MonstroVfx("shake", "Impact Shake"), 
    MonstroVfx("strobe", "Psycho Strobe"),
    MonstroVfx("glitch", "Digital Glitch"), 
    MonstroVfx("smartzoom", "Auto Zoom"),
    MonstroVfx("rgb", "RGB Split"),
    MonstroVfx("motionblur", "Motion Blur")
)

val ColorLibrary = listOf(
    MonstroPreset("none", "Estado Bruto", "Original"),
    MonstroPreset("trap", "Trap Lord", "Vibrante"),
    MonstroPreset("dark", "Dark Energy", "Industrial"),
    MonstroPreset("cinema", "Noir City", "P&B"),
    MonstroPreset("acid", "Acid Trip", "Psicodélico")
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
    
    // --- ESTADOS GERAIS ---
    var clips by remember { mutableStateOf(emptyList<MonstroClip>()) }
    var indiceAtivo by remember { mutableIntStateOf(0) }
    var abaSelecionada by remember { mutableIntStateOf(0) }
    var vfxAtivos by remember { mutableStateOf(setOf<String>()) }
    var masterZoom by remember { mutableFloatStateOf(1f) }
    var exportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableFloatStateOf(0f) }

    // --- MOTOR DE ANIMAÇÃO (DECLARADO NO TOPO PARA EVITAR ERROS DE ESCOPO) ---
    val infiniteTransition = rememberInfiniteTransition(label = "MonstroEngine")
    
    val shakeAnim by infiniteTransition.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(40, easing = LinearEasing), RepeatMode.Reverse), label = "Shake"
    )
    
    val strobeAnim by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(60, easing = LinearEasing), RepeatMode.Reverse), label = "Strobe"
    )

    val autoZoomAnim by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Zoom"
    )

    val rotateRed by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse), label = "Red"
    )

    val rotateBlue by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -15f,
        animationSpec = infiniteRepeatable(tween(200, easing = LinearEasing), RepeatMode.Reverse), label = "Blue"
    )

    // --- LOGICA DE GLITCH ---
    var glitchX by remember { mutableStateOf(0f) }
    LaunchedEffect(vfxAtivos.contains("glitch")) {
        while(vfxAtivos.contains("glitch")) {
            glitchX = if (Random.nextFloat() > 0.85f) Random.nextFloat() * 20f else 0f
            delay(100)
        }
        glitchX = 0f
    }

    // --- MOTOR EXOPLAYER ---
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    // --- PROCESSAMENTO DE COR ---
    val currentPreset = clips.getOrNull(indiceAtivo)?.preset ?: "none"
    val matiz = remember(currentPreset, vfxAtivos) {
        val matrix = ColorMatrix()
        when(currentPreset) {
            "trap" -> matrix.setToSaturation(2.5f)
            "cinema" -> matrix.setToSaturation(0f)
            "dark" -> matrix.apply { 
                val m = values
                m[0] = 1.5f; m[6] = 1.5f; m[12] = 1.5f
                m[4] = -25f; m[9] = -25f; m[14] = -25f 
            }
            "acid" -> matrix.setToSaturation(3f)
            else -> matrix.setToSaturation(1f)
        }
        matrix
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            clips = clips + MonstroClip(it)
            exoPlayer.addMediaItem(MediaItem.fromUri(it)); exoPlayer.prepare(); exoPlayer.play()
        }
    }
    
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(it) launcher.launch("video/*") }

    Scaffold(containerColor = MonstroBg) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            
            // HEADER
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("MONSTRO V18.2", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(EmeraldTurbo, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("AI ENGINE: ACTIVE", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = { /* Configs */ }) {
                    Icon(Icons.Default.PlayArrow, null, tint = MonstroAccent)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // PREVIEW ENGINE (VFX DINÂMICO)
            Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(12.dp)).background(DarkGrey)) {
                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxSize().clickable { permLauncher.launch(perm) }, Alignment.Center) {
                        Text("IMPORT MASTER", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                } else {
                    key(clips.getOrNull(indiceAtivo)?.uri) {
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
                                val isGlitch = vfxAtivos.contains("glitch")
                                val isRGB = vfxAtivos.contains("rgb")

                                translationX = if(isShake) shakeAnim else 0f + if(isGlitch) glitchX else 0f
                                alpha = if(isStrobe && strobeAnim < 0.5f) 0.1f else 1f
                                scaleX = masterZoom * (if(isAutoZoom) autoZoomAnim else 1f)
                                scaleY = masterZoom * (if(isAutoZoom) autoZoomAnim else 1f)

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val androidMatrix = android.graphics.ColorMatrix(matiz.values.clone())
                                    
                                    // APLICAÇÃO SEGURA DE ROTAÇÃO PARA RGB SPLIT OU ACID TRIP
                                    if (isRGB || currentPreset == "acid") {
                                        androidMatrix.setRotate(0, rotateRed) 
                                        androidMatrix.setRotate(2, rotateBlue) 
                                    }

                                    val filter = android.graphics.RenderEffect.createColorFilterEffect(
                                        android.graphics.ColorMatrixColorFilter(androidMatrix)
                                    )
                                    
                                    renderEffect = if(vfxAtivos.contains("motionblur")) {
                                        android.graphics.RenderEffect.createBlurEffect(12f, 4f, android.graphics.Shader.TileMode.CLAMP)
                                    } else {
                                        filter
                                    }.asComposeRenderEffect()
                                }
                            }
                        )
                    }
                }
                if (exportando) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)), Alignment.Center) {
                        CircularProgressIndicator(color = MonstroAccent, progress = progressoExport)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // TIMELINE
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(clips) { i, _ ->
                    Box(Modifier.size(90.dp, 45.dp).clip(RoundedCornerShape(8.dp)).background(if(i == indiceAtivo) MonstroAccent.copy(0.15f) else DarkGrey).border(2.dp, if(i == indiceAtivo) MonstroAccent else Color.Transparent, RoundedCornerShape(8.dp)).clickable { 
                        indiceAtivo = i
                        exoPlayer.seekToDefaultPosition(i) 
                    }, Alignment.Center) {
                        Text("V$i", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // PAINEL DE CONTROLE
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

            // RENDERIZADOR
            Button(
                onClick = {
                    exportando = true
                    scope.launch {
                        progressoExport = 0f
                        while(progressoExport < 1f) { delay(40); progressoExport += 0.02f }
                        exportando = false
                        Toast.makeText(context, "CONCLUÍDO!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent),
                enabled = clips.isNotEmpty() && !exportando
            ) {
                Text("RENDERIZAR V18.2", fontWeight = FontWeight.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCenter(aba: Int, onAbaChange: (Int) -> Unit, vfxAtivos: Set<String>, onVfxClick: (String) -> Unit, zoom: Float, onZoomChange: (Float) -> Unit, clips: List<MonstroClip>, activeIdx: Int, onPresetClick: (String) -> Unit) {
    Column {
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
            Tab(selected = aba == 0, onClick = { onAbaChange(0) }) { Text("CHAOS", Modifier.padding(10.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
            Tab(selected = aba == 1, onClick = { onAbaChange(1) }) { Text("CORES", Modifier.padding(10.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
        }
        
        Box(Modifier.height(170.dp).padding(top = 10.dp)) {
            if (aba == 0) {
                Column {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2), 
                        modifier = Modifier.height(110.dp).nestedScroll(remember { object : NestedScrollConnection {} }), 
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ChaosEffects) { fx ->
                            val active = vfxAtivos.contains(fx.id)
                            Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp)).background(if(active) MonstroAccent else DarkGrey).clickable { onVfxClick(fx.id) }, Alignment.Center) {
                                Text(fx.nome.uppercase(), color = if(active) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Slider(value = zoom, onValueChange = onZoomChange, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent, activeTrackColor = MonstroAccent))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.nestedScroll(remember { object : NestedScrollConnection {} })
                ) {
                    items(ColorLibrary) { p ->
                        val sel = clips.getOrNull(activeIdx)?.preset == p.id
                        Box(Modifier.height(45.dp).clip(RoundedCornerShape(8.dp)).background(if(sel) MonstroPink.copy(0.2f) else DarkGrey).border(1.dp, if(sel) MonstroPink else Color.Transparent, RoundedCornerShape(8.dp)).clickable { onPresetClick(p.id) }.padding(8.dp), Alignment.Center) {
                            Text(p.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

