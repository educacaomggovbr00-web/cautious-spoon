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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFa855f7))) {
                MonstroEditorScreen()
            }
        }
    }
}

@Composable
fun MonstroEditorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var clips by remember { mutableStateOf(listOf<Uri>()) }
    var exportando by remember { mutableStateOf(false) }
    var progresso by remember { mutableStateOf(0f) }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { 
            clips = clips + it
            exoPlayer.addMediaItem(MediaItem.fromUri(it))
            exoPlayer.prepare()
        }
    }

    val permissao = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
        Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

    val launchPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) seletor.launch("video/*")
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF020306)).padding(16.dp)) {
        Text("MONSTRO V18", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Spacer(Modifier.height(20.dp))

        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF121214))) {
            if (clips.isEmpty()) {
                IconButton(onClick = { launchPermissao.launch(permissao) }, Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Add, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                }
            } else {
                AndroidView(factory = { PlayerView(it).apply { player = exoPlayer; useController = false } }, Modifier.fillMaxSize())
            }
        }

        Spacer(Modifier.height(20.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(clips) { index, _ ->
                Box(Modifier.size(80.dp, 50.dp).background(Color(0xFFa855f7)).clickable { exoPlayer.seekToDefaultPosition(index) })
            }
        }

        Spacer(Modifier.weight(1f))
        if (exportando) LinearProgressIndicator(progress = progresso, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                exportando = true
                scope.launch {
                    while(progresso < 1f) { delay(50); progresso += 0.02f }
                    exportando = false
                    Toast.makeText(context, "VÍDEO EXPORTADO!", Toast.LENGTH_SHORT).show()
                }
            },
            Modifier.fillMaxWidth(),
            enabled = clips.isNotEmpty() && !exportando
        ) { Text("RENDERIZAR") }
    }
}
