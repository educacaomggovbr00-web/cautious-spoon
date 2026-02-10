package com.ncorti.kotlin.template.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Fundo escuro para combinar com o Estilo Monstro
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF020306)) {
                MonstroLanding()
            }
        }
    }
}

@Composable
fun MonstroLanding() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MONSTRO V18",
            color = Color(0xFFa855f7),
            fontSize = 32.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "SISTEMA ATIVO",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(32.dp))
        CircularProgressIndicator(color = Color(0xFFdb2777))
    }
}
