package com.cbrl.pixelblaze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cbrl.pixelblaze.ui.HelmetApp
import com.cbrl.pixelblaze.ui.theme.PixelblazePhoneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PixelblazePhoneTheme { HelmetApp() } }
    }
}
