package com.game.gametong.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.OptionCard
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.SectionTitle
import com.game.gametong.ui.theme.Inks

/** 구슬치기 놀이 고르기. */
@Composable
fun MarbleScreen(
    onBack: () -> Unit,
    onStart: (MarbleMode, Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(MarbleMode.CIRCLE) }
    var twoPlayer by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "구슬치기", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle("어떤 구슬놀이를 할까?")
                for (item in MarbleMode.entries) {
                    OptionCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        emoji = item.emoji,
                        selected = item == mode,
                        color = Inks.Blue,
                        modifier = Modifier.fillMaxWidth(),
                    ) { mode = item }
                }

                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 12.dp,
                    color = Color(0xFFFFF3D6),
                ) {
                    Column {
                        Text(mode.title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                        Spacer(Modifier.height(4.dp))
                        Text(mode.howTitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Inks.Ink)
                    }
                }

                SwitchRow(
                    title = if (twoPlayer) "2인 번갈아 (한 기기)" else "AI와 대전",
                    subtitle = if (twoPlayer) "두 사람이 번갈아 칩니다." else "AI가 각도를 계산해 칩니다.",
                    checked = twoPlayer,
                    onChange = { twoPlayer = it },
                )

                FunButton(
                    text = "${mode.title} 시작",
                    modifier = Modifier.fillMaxWidth(),
                    color = Inks.Blue,
                    fontSize = 20,
                ) { onStart(mode, twoPlayer) }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** 구슬치기 두 놀이. */
enum class MarbleMode(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val howTitle: String,
) {
    CIRCLE(
        "원 안 구슬 쳐내기",
        "원 안 유리구슬을 왕구슬로 쳐내 따먹기",
        "🔮",
        "원 안에 모인 유리구슬을 왕구슬로 튕겨 원 밖으로 쳐내면 한 개씩 따먹습니다. 먼저 3개(또는 8번씩 친 뒤 다수)면 승리!",
    ),
    HOLE(
        "구멍치기",
        "구멍 3개를 순서대로 넣고 상대 구슬 맞히기",
        "🕳️",
        "구멍 1 → 2 → 3을 순서대로 넣은 뒤 상대 구슬을 맞히면 따먹습니다. 성공하면 한 번 더 칩니다.",
    ),
}
