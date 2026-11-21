package com.game.gametong.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.R
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.theme.Inks

@Composable
fun IntroScreen(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Inks.Kraft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onStart,
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.gametong_intro),
            contentDescription = "우리동네 게임통 인트로",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.62f to Color.Transparent,
                        1f to Inks.Wood.copy(alpha = 0.72f),
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "눌러서 시작",
                color = Inks.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            FunButton(
                text = "게임 시작",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 22,
                onClick = onStart,
            )
            Box(
                modifier = Modifier
                    .background(Inks.Wood.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "체험판 v0.1.0-demo · 지우개 따먹기 두 가지 모드 플레이 가능",
                    color = Inks.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
