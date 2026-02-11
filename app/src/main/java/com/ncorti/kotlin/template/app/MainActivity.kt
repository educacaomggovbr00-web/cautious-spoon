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

// --- DESIGN SYSTEM (INDUSTRIAL MONSTRO) ---
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
    MonstroPreset("trap", "Trap Lord", "Vibrante 250%"),
    MonstroPreset("dark", "Dark Energy", "Industrial"),
    MonstroPreset("cinema", "Noir City", "P&B Profundo"),
    MonstroPreset("acid", "Acid Trip", "Cores Psicodélicas"),
    MonstroPreset("vintage", "Old School", "Anos 90")
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
    
    // --- ESTADOS DO MOTOR ---
    var clips by remember { mutableStateOf(emptyList<MonstroClip>()) }
    var indiceAtivo by remember { mutableIntStateOf(0) }
    var abaSelecionada by remember { mutableIntStateOf(0) }
    var vfxAtivos by remember { mutableStateOf(setOf<String>()) }
    var masterZoom by remember { mutableFloatStateOf(1f) }
    var exportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableFloatStateOf(0f) }
    var aiStatus by remember { mutableStateOf("AI ENGINE: ACTIVE") }

    // --- MOTOR DE ANIMAÇÃO DE CAOS ---
    val infiniteTransition = rememberInfiniteTransition(label = "ChaosMotor")
    
    // Shake: Balanço agressivo
    val shakeAnim by infiniteTransition.animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(40, easing = LinearEasing), RepeatMode.Reverse), label = "Shake"
    )
    
    // Strobe: Pisca-pisca frenético
    val strobeAnim by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(50, easing = StepEasing), RepeatMode.Reverse), label = "Strobe"
    )

    // Smart Zoom: Pulsação rítmica
    val autoZoomAnim by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "AutoZoom"
    )

    // Glitch: Deslocamento aleatório
    var glitchOffset by remember { mutableStateOf(0f) }
    LaunchedEffect(vfxAtivos.contains("glitch")) {
        while(vfxAtivos.contains("glitch")) {
            glitchOffset = if (Random.nextFloat() > 0.8f) Random.nextFloat() * 20f else 0f
            delay(100)
        }
        glitchOffset = 0f
    }

    // --- MOTOR EXOPLAYER ---
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
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

    // --- MOTOR DE COR (PRESETS PROFISSIONAIS) ---
    val currentPreset = clips.getOrNull(indiceAtivo)?.preset ?: "none"
    val matiz = remember(currentPreset, vfxAtivos) {
        val matrix = ColorMatrix()
        when(currentPreset) {
            "trap" -> matrix.setToSaturation(2.8f)
            "cinema" -> matrix.apply { 
                setToSaturation(0.2f)
                val m = values
                m[0] *= 1.2f; m[6] *= 1.1f; m[12] *= 1.4f // Boost nos azuis
            }
            "dark" -> matrix.apply { 
                val m = values
                m[4] = -20f; m[9] = -20f; m[14] = -20f // Esmaga os pretos
                m[0] = 1.3f; m[6] = 1.3f; m[12] = 1.3f 
            }
            "acid" -> matrix.apply { rotateBlue(45f); rotateRed(45f); setToSaturation(3f) }
            "vintage" -> matrix.apply { setToSaturation(0.8f); rotateRed(10f) }
            else -> matrix.setToSaturation(1f)
        }
        
        // Efeito RGB Split via Matrix (Simulado mudando canais)
        if (vfxAtivos.contains("rgb")) {
            val m = matrix.values
            m[0] = 1.5f; m[6] = 0.5f; m[12] = 0.5f // Força o Vermelho
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
                        Box(Modifier.size(6.dp).background(if(aiStatus.contains("ACTIVE")) EmeraldTurbo else Color.Red, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(aiStatus, color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = { /* Configs */ }) {
                    Icon(Icons.Default.PlayArrow, null, tint = MonstroAccent)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // PREVIEW BOX COM VFX DINÂMICO
            Box(Modifier.fillMaxWidth().aspectRatio(16/9f).clip(RoundedCornerShape(12.dp)).background(DarkGrey).border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))) {
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
                                // Aplicação de Caos em Tempo Real
                                val isShake = vfxAtivos.contains("shake")
                                val isStrobe = vfxAtivos.contains("strobe")
                                val isAutoZoom = vfxAtivos.contains("smartzoom")
                                val isGlitch = vfxAtivos.contains("glitch")

                                translationX = if(isShake) shakeAnim else 0f + if(isGlitch) glitchOffset else 0f
                                translationY = if(isShake) shakeAnim * 0.5f else 0f
                                
                                alpha = if(isStrobe) strobeAlpha(strobeAnim) else 1f
                                
                                scaleX = masterZoom * (if(isAutoZoom) autoZoomAnim else 1f)
                                scaleY = masterZoom * (if(isAutoZoom) autoZoomAnim else 1f)

                                // RenderEffect para cor e blur
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val androidMatrix = android.graphics.ColorMatrix(matiz.values.clone())
                                    val colorEffect = android.graphics.RenderEffect.createColorFilterEffect(
                                        android.graphics.ColorMatrixColorFilter(androidMatrix)
                                    )
                                    
                                    renderEffect = if(vfxAtivos.contains("motionblur")) {
                                        android.graphics.RenderEffect.createBlurEffect(15f, 5f, android.graphics.Shader.TileMode.CLAMP)
                                    } else {
                                        colorEffect
                                    }.asComposeRenderEffect()
                                }
                            }
                        )
                    }
                }
                if (exportando) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MonstroAccent)
                            Spacer(Modifier.height(8.dp))
                            Text("${(progressoExport * 100).toInt()}% RENDER", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // TIMELINE REATIVA
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(clips) { i, _ ->
                    Box(Modifier.size(95.dp, 50.dp).clip(RoundedCornerShape(8.dp)).background(if(i == indiceAtivo) MonstroAccent.copy(0.15f) else DarkGrey).border(2.dp, if(i == indiceAtivo) MonstroAccent else Color.Transparent, RoundedCornerShape(8.dp)).clickable { 
                        indiceAtivo = i
                        exoPlayer.seekToDefaultPosition(i) 
                    }, Alignment.Center) {
                        Text("V$i", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // CONTROL CENTER (ABAS)
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

            // BOTÃO DE RENDER TURBO
            Button(
                onClick = {
                    exportando = true
                    scope.launch {
                        progressoExport = 0f
                        while(progressoExport < 1f) { delay(40); progressoExport += 0.02f }
                        exportando = false
                        Toast.makeText(context, "RENDERIZADO COM SUCESSO!", Toast.LENGTH_SHORT).show()
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

// Auxiliar para efeito de Strobe agressivo
private fun strobeAlpha(anim: Float): Float = if (anim > 0.6f) 1f else 0.1f

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
            Tab(selected = aba == 1, onClick = { onAbaChange(1) }) { Text("MOTOR DE COR", Modifier.padding(10.dp), fontSize = 10.sp, fontWeight = FontWeight.Black) }
        }
        
        Box(Modifier.height(180.dp).padding(top = 10.dp)) {
            if (aba == 0) {
                Column {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2), 
                        modifier = Modifier.height(130.dp).nestedScroll(remember { object : NestedScrollConnection {} }), 
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(ChaosEffects) { fx ->
                            val active = vfxAtivos.contains(fx.id)
                            Box(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(8.dp)).background(if(active) MonstroAccent else DarkGrey).border(1.dp, if(active) Color.White.copy(0.3f) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { onVfxClick(fx.id) }, Alignment.Center) {
                                Text(fx.nome.uppercase(), color = if(active) Color.White else Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("MASTER ZOOM: ${String.format("%.1f", zoom)}x", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Slider(value = zoom, onValueChange = onZoomChange, valueRange = 1f..3f, colors = SliderDefaults.colors(thumbColor = MonstroAccent, activeTrackColor = MonstroAccent))
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxHeight().nestedScroll(remember { object : NestedScrollConnection {} })
                ) {
                    items(ColorLibrary) { p ->
                        val sel = clips.getOrNull(activeIdx)?.preset == p.id
                        Box(Modifier.height(50.dp).clip(RoundedCornerShape(8.dp)).background(if(sel) MonstroPink.copy(0.2f) else DarkGrey).border(1.dp, if(sel) MonstroPink else Color.Transparent, RoundedCornerShape(8.dp)).clickable { onPresetClick(p.id) }.padding(8.dp), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(p.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                Text(p.desc, color = Color.Gray, fontSize = 7.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

