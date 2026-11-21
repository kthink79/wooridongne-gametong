package com.game.gametong.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.ui.PlayerState
import com.game.gametong.ui.components.DemoNotice
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.theme.Inks

private data class RankRow(val rank: Int, val name: String, val fame: Int, val me: Boolean = false)

private fun townRanks(fame: Int): List<RankRow> = listOf(
    RankRow(1, "탕정불도저", 4120),
    RankRow(2, "문방구큰아들", 3880),
    RankRow(3, "딱지한장", 3450),
    RankRow(4, "골목대장K", 3120),
    RankRow(5, "구슬서리꾼", 2760),
    RankRow(6, "삼거리왕", 2340),
    RankRow(7, "개구쟁이 (나)", fame, me = true),
    RankRow(8, "새벽공기", 1120),
    RankRow(9, "지우개도둑", 980),
    RankRow(10, "탕정불주먹", 870),
)

private val nearRanks = listOf(
    RankRow(1, "배방역왕", 5210),
    RankRow(2, "아산딱지", 4780),
    RankRow(3, "온양구슬", 4410),
    RankRow(4, "탕정불도저", 4120),
    RankRow(5, "음봉지우개", 3990),
)

private val cityRanks = listOf(
    RankRow(1, "온양신도시", 8120),
    RankRow(2, "배방역왕", 5210),
    RankRow(3, "아산딱지", 4780),
    RankRow(4, "탕정불도저", 4120),
    RankRow(5, "음봉지우개", 3990),
)

@Composable
fun RankingScreen(player: PlayerState, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val rows = when (tab) {
        0 -> townRanks(player.fame)
        1 -> nearRanks
        else -> cityRanks
    }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep)))) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "랭킹", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("내 동네", "인접 지역", "시·군·구").forEachIndexed { index, label ->
                        val selected = tab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Inks.Wood else Inks.Paper)
                                .border(2.dp, Inks.Wood, RoundedCornerShape(12.dp))
                                .clickable { tab = index }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (selected) Inks.White else Inks.Ink,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                DemoNotice("체험판 — 데모용 예시 순위표입니다. 실제 집계는 서버 연동 후.")

                Spacer(Modifier.height(10.dp))
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 10.dp) {
                    Column {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (row.me) Inks.Gold.copy(alpha = 0.28f) else Color.Transparent)
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            when (row.rank) {
                                                1 -> Inks.Gold
                                                2 -> Color(0xFFB9B9B9)
                                                3 -> Color(0xFFC08A4E)
                                                else -> Inks.Wood.copy(alpha = 0.18f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${row.rank}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (row.rank <= 3) Inks.Ink else Inks.SoftInk,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    row.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (row.me) FontWeight.Black else FontWeight.Bold,
                                    color = Inks.Ink,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    String.format("%,d", row.fame),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (row.me) Inks.Red else Inks.SoftInk,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (tab == 0) "10위까지 ${(rows.last().fame - player.fame).coerceAtLeast(0)}점 남았습니다 (예시)" else "상위 지역으로 갈수록 배율이 올라갑니다",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Inks.SoftInk,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
