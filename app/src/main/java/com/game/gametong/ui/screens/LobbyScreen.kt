package com.game.gametong.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.ui.PlayerState
import com.game.gametong.ui.Route
import com.game.gametong.ui.components.InfoRow
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.theme.Inks

private data class Tile(
    val emoji: String,
    val label: String,
    val color: Color,
    val route: Route,
)

private val tiles = listOf(
    Tile("🟥", "딱지치기", Inks.Red, Route.Ttakji),
    Tile("🔮", "구슬치기", Inks.Blue, Route.Marble),
    Tile("🧽", "지우개 따먹기", Inks.Green, Route.EraserMenu),
    Tile("🫘", "공기놀이", Inks.Gold, Route.Gonggi),
    Tile("🎁", "이벤트 뿌리기", Inks.Purple, Route.Sprinkle),
    Tile("🕹️", "연습게임", Inks.Teal, Route.EraserMenu),
    Tile("🏆", "랭킹", Inks.Orange, Route.Ranking),
    Tile("⚙️", "설정", Color(0xFF7A7466), Route.Settings),
)

@Composable
fun LobbyScreen(
    player: PlayerState,
    onOpen: (Route) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            NoticeBanner()
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MyInfoCard(player, Modifier.weight(1.12f))
                CharacterCard(player, Modifier.weight(0.88f))
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "동네 놀이판",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Inks.Wood,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            GameGrid(onOpen)
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Inks.Wood.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "체험판 v0.1.0-demo — 지우개 따먹기 두 모드는 실제 플레이 가능, " +
                        "딱지·구슬·공기놀이는 맛보기 데모입니다. 랭킹·명성은 아직 기기에만 저장됩니다.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Inks.SoftInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NoticeBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(Inks.Wood)
            .border(3.dp, Inks.Wood, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Inks.Red)
                    .border(2.dp, Inks.White.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("공지", color = Inks.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "첫 접속 이벤트 진행중 · 탕정면 놀이판 오픈!",
                color = Inks.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text("›", color = Inks.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MyInfoCard(player: PlayerState, modifier: Modifier = Modifier) {
    PaperCard(modifier = modifier, padding = 12.dp) {
        Column {
            Text(
                text = "★ 내 정보 ★",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Inks.Wood,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Inks.Wood.copy(alpha = 0.25f))
            )
            Spacer(Modifier.height(4.dp))
            InfoRow("📍", "내 지역", player.region)
            InfoRow("🏅", "명성", String.format("%,d", player.fame))
            InfoRow("🟥", "딱지 랭킹", "${player.ttakjiRank}위")
            InfoRow("🔮", "구슬 랭킹", "${player.marbleRank}위")
            InfoRow("🧽", "지우개 랭킹", "${player.eraserRank}위")
        }
    }
}

@Composable
private fun CharacterCard(player: PlayerState, modifier: Modifier = Modifier) {
    PaperCard(modifier = modifier, padding = 8.dp, color = Inks.Paper) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .aspectRatio(0.86f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFFFE9BE), Color(0xFFF2CE92)))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("🧒", fontSize = 62.sp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Inks.Wood)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Lv.${player.level}", color = Inks.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Inks.Wood)
                    .padding(vertical = 5.dp)
            ) {
                Text(
                    text = player.nickname,
                    color = Inks.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun GameGrid(onOpen: (Route) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rowIndex in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (colIndex in 0 until 4) {
                    val tile = tiles[rowIndex * 4 + colIndex]
                    GameTile(tile, Modifier.weight(1f)) { onOpen(tile.route) }
                }
            }
        }
    }
}

@Composable
private fun GameTile(tile: Tile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(tile.color, tile.color.copy(alpha = 0.82f))))
            .border(3.dp, Inks.Wood, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tile.emoji, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = tile.label,
            color = Inks.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.height(1.dp))
    }
}
