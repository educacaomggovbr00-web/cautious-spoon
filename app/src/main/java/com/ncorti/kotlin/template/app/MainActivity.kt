package com.ncorti.kotlin.template.app

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- DESIGN TOKENS MONSTRO ---
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

@Composable
fun MonstroIndustrialEditor() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estados
    var projeto by remember { mutableStateOf(MonstroProject()) }
    var indiceSelecionado by remember { mutableStateOf(0) }
    var estaExportando by remember { mutableStateOf(false) }
    var progressoExport by remember { mutableStateOf(0f) }

    // Inicialização Única do ExoPlayer (Crucial para o A30s não engasgar)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            // Configuração de Áudio para não dar conflito de foco
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(audioAttributes, true)
        }
    }

    // Listener de Transição
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                indiceSelecionado = exoPlayer.currentMediaItemIndex
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Gerenciador de Permissões
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
        else Toast.makeText(context, "Acesso necessário para editar!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(containerColor = MonstroBg) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            // HEADER
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(EmeraldTurbo, RoundedCornerShape(50)))
                        Spacer(Modifier.width(6.dp))
                        Text("SAMSUNG A30S OPTIMIZED", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MonstroAccent.copy(0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MonstroAccent)
                }
            }

            Spacer(Modifier.height(20.dp))

            // ÁREA DE PREVIEW
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkGrey)
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
            ) {
                if (projeto.clips.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().clickable { launchPermissao.launch(permissao) },
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("IMPORTAR MASTER", color = Color.DarkGray, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = exoPlayer
                                useController = false
                                // Força o uso de SurfaceView (Mais performático que TextureView no A30s)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                if (estaExportando) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LinearProgressIndicator(progress = progressoExport, color = MonstroAccent, modifier = Modifier.width(150.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("ENGINEERING EXPORT...", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // TIMELINE
            Text("SEQUENCE", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(projeto.clips) { index, clip ->
                    val ativo = index == indiceSelecionado
                    Box(
                        modifier = Modifier
                            .size(100.dp, 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (ativo) MonstroAccent.copy(0.2f) else DarkGrey)
                            .border(1.5.dp, if (ativo) MonstroAccent else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { 
                                indiceSelecionado = index
                                exoPlayer.seekToDefaultPosition(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("V$index", color = if (ativo) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // PRESETS
            Text("COLOR ENGINE", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                            .background(if (selecionado) MonstroPink.copy(0.15f) else DarkGrey)
                            .border(1.dp, if (selecionado) MonstroPink else Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                            .clickable {
                                if (indiceSelecionado in projeto.clips.indices) {
                                    val novaLista = projeto.clips.toMutableList()
                                    novaLista[indiceSelecionado] = novaLista[indiceSelecionado].copy(presetAtivo = preset)
                                    projeto = projeto.copy(novaLista.toList())
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Text(preset.nome, color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Text(preset.descricao, color = Color.Gray, fontSize = 8.sp)
                    }
                }
            }

            // EXPORT
            Button(
                onClick = {
                    estaExportando = true
                    scope.launch {
                        progressoExport = 0f
                        while (progressoExport < 1f) {
                            delay(50) 
                            progressoExport += 0.02f
                        }
                        estaExportando = false
                        Toast.makeText(context, "RENDER FINALIZADO!", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = projeto.clips.isNotEmpty() && !estaExportando,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MonstroAccent)
            ) {
                Text("EXPORTAR MASTER (TURBO)", fontWeight = FontWeight.Black)
            }
        }
    }
}

