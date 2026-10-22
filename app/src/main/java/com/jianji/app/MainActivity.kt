package com.jianji.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jianji.app.ui.JianJiRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式：内容延伸到状态栏/导航栏，由 Compose 的 Insets 负责避让
        enableEdgeToEdge()
        setContent {
            JianJiRoot()
        }
    }
}
