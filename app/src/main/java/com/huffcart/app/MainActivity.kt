package com.huffcart.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.huffcart.app.ui.HuffcartApp
import com.huffcart.app.ui.theme.HuffcartTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HuffcartTheme {
                HuffcartApp()
            }
        }
    }
}
