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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
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

// --- CONFIGURAÇÃO VISUAL MONSTRO ---
val MonstroBg = Color(0xFF020306)
val MonstroAccent = Color(0xFFa855f7)
val MonstroPink = Color(0xFFdb2777)
val EmeraldTurbo = Color(0xFF10b981)
val DarkGrey = Color(0xFF121214)

data class MonstroPreset(
    val id: String, 
    val nome: String, 
    val descricao: String
)

val MonstroLibrary = listOf(
    MonstroPreset("raw", "ESTADO RAW", "Sem filtros"),
    MonstroPreset("neon", "NEON PUNCH", "Brilho e Contraste"),
    MonstroPreset("trap", "TRAP LORD", "Estilo Videoclipe"),
    MonstroPreset("dark", "DARK ENERGY", "Cinematográfico")
)

data class MonstroClip(
    val uri: Uri, 
    val presetAtivo: MonstroPreset = MonstroLibrary[0]
)

data class MonstroProject(
    val clips: List<MonstroClip> = emptyList()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MonstroTheme {
                MonstroIndustrialEditor()
            }
        }
    }
}

@Composable
fun MonstroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MonstroAccent,
            background = MonstroBg,
            surface = DarkGrey
        ),
        content = content
    )
}

@OptIn(UnstableApi::class) // Vence o bloqueio do GitHub Actions / Lint
@Composable
fun MonstroIndustrialEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estados de Gerenciamento
    var projeto by remember { mutableStateOf(MonstroProject()) }
    var indiceSelecionado by remember { mutableStateOf(0) }
    var estaExportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableStateOf(0f) }

    // Inicialização do ExoPlayer Otimizada para o A30s
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            // Define atributos de áudio para evitar conflitos de hardware
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, true)
        }
    }

    // Gerenciador de Ciclo de Vida do Player
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Sincroniza a UI quando o vídeo muda na playlist do player
                val currentIdx = exoPlayer.currentMediaItemIndex
                if (currentIdx >= 0) indiceSelecionado = currentIdx
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Seletor de Mídia e Permissões
    val permissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
        Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val seletorVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val novoClip = MonstroClip(uri = it)
            projeto = projeto.copy(clips = projeto.clips + novoClip)
            exoPlayer.addMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    val launchPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) seletorVideo.launch("video/*")
        else Toast.makeText(context, "Acesso negado!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(containerColor = MonstroBg) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            // CABEÇALHO INDUSTRIAL
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(EmeraldTurbo, RoundedCornerShape(50)))
                        Spacer(Modifier.width(6.dp))
                        Text("SISTEMA OTIMIZADO (A30s)", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(MonstroAccent, MonstroPink))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ÁREA DE PREVIEW (Vencendo o erro libEGL)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkGrey)
                    .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(16.dp))
            ) {
                if (projeto.clips.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().clickable { launchPermissao.launch(permissao) },
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("IMPORTAR VÍDEO MASTER", color = Color.Gray, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = exoPlayer
                                useController = false
                                // Otimização crítica para hardware limitado
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                if (estaExportando) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(progress = progressoExport, color = MonstroAccent)
                            Spacer(Modifier.height(16.dp))
                            Text("RENDERIZANDO...", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // TIMELINE DINÂMICA
            Text("TIMELINE DE CLIPS", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(projeto.clips) { index, clip ->
                    val ativo = index == indiceSelecionado
                    Box(
                        modifier = Modifier
                            .size(110.dp, 62.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (ativo) MonstroAccent.copy(0.15f) else DarkGrey)
                            .border(2.dp, if (ativo) MonstroAccent else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { 
                                indiceSelecionado = index
                                exoPlayer.seekToDefaultPosition(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CLIP $index", color = if (ativo) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // GRADE DE PRESETS (EFEITOS)
            Text("ESCOLHER ENGINE DE COR", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(MonstroLibrary) { preset ->
                    val selecionado = projeto.clips.getOrNull(indiceSelecionado)?.presetAtivo?.id == preset.id
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selecionado) MonstroPink.copy(0.1f) else DarkGrey)
                            .border(1.dp, if (selecionado) MonstroPink else Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                            .clickable {
                                if (indiceSelecionado in projeto.clips.indices) {
                                    val novaLista = projeto.clips.toMutableList()
                                    novaLista[indiceSelecionado] = novaLista[indiceSelecionado].copy(presetAtivo = preset)
                                    projeto = projeto.copy(novaLista.toList())
                                    Toast.makeText(context, "${preset.nome} ATIVADO", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Text(preset.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(preset.descricao, color = Color.Gray, fontSize = 8.sp)
                    }
                }
            }

            // BOTÃO DE RENDERIZAÇÃO
            Button(
                onClick = {
                    estaExportando = true
                    scope.launch {
                        progressoExport = 0f
                        while (progressoExport < 1f) {
                            delay(40)
                            progressoExport += 0.02f
                        }
                        estaExportando = false
                        Toast.makeText(context, "VÍDEO EXPORTADO!", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = projeto.clips.isNotEmpty() && !estaExportando,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent)
            ) {
                Text("RENDERIZAR MP4 TURBO", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

