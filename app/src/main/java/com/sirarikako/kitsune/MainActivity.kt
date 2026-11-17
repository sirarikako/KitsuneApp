package com.sirarikako.kitsune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sirarikako.kitsune.core.data.ThemePreferences
import com.sirarikako.kitsune.core.theme.KitsuneTheme
import com.sirarikako.kitsune.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: KitsuneViewModel = viewModel()
            val data by vm.data.collectAsStateWithLifecycle()
            KitsuneTheme(data?.theme ?: ThemePreferences()) {
                val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
                SideEffect {
                    val style = if (light) SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(style, style)
                }
                KitsuneApp(vm, data)
            }
        }
    }
}
