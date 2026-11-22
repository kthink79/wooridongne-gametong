package com.game.gametong

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.game.gametong.ui.GametongApp
import com.game.gametong.ui.theme.GametongTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면 끝까지 그리되, 각 화면이 안전 영역(상태 바·내비게이션 바)을 직접 비켜 배치한다.
        enableEdgeToEdge()
        setContent {
            GametongTheme {
                GametongApp()
            }
        }
    }
}
