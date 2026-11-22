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
    val detail: String,
    val color: Color,
    val route: Route,
)

private val playTiles = listOf(
    Tile("🟥", "딱지치기", "네모 뒤집기 2.5D\n동그란 3종", Inks.Red, Route.Ttakji),
    Tile("🔮", "구슬치기", "원 안 쳐내기\n구멍치기", Inks.Blue, Route.Marble),
    Tile("🧽", "지우개 따먹기", "알까기\n레슬링 3카운트", Inks.Green, Route.EraserMenu),
)

private val extraTiles = listOf(
    Tile("🫘", "공기놀이", "타이밍 잡기", Inks.Gold, Route.Gonggi),
    Tile("🎁", "이벤트 뿌리기", "명성 올리기", Inks.Purple, Route.Sprinkle),
    Tile("🏆", "랭킹", "동네 순위", Inks.Orange, Route.Ranking),
    Tile("⚙️", "설정", "조준·진동", Color(0xFF7A7466), Route.Settings),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tile in playTiles) {
                    PlayTile(tile, Modifier.weight(1f)) { onOpen(tile.route) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tile in extraTiles) {
                    MiniTile(tile, Modifier.weight(1f)) { onOpen(tile.route) }
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Inks.Wood.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "체험판 v0.3.0 — 네모 딱지는 2.5D 물리(들림·웨이블·튕김)와 타격 위치·조준 지점·파워 조작, " +
                        "지우개는 3D 블록·넘기며 전진·걸쳐 올라가기·책상 표면 마찰까지 들어갔습니다. " +
                        "공기놀이·뿌리기는 맛보기 데모입니다.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Inks.SoftInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(18.dp))
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
                text = "딱지·구슬 정식 종목 오픈! 탕정면 놀이판에서 겨뤄 보세요",
                color = Inks.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
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
private fun PlayTile(tile: Tile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .shadow(5.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(tile.color, tile.color.copy(alpha = 0.82f))))
            .border(3.dp, Inks.Wood, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tile.emoji, fontSize = 34.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = tile.label,
            color = Inks.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tile.detail,
            color = Inks.White.copy(alpha = 0.92f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun MiniTile(tile: Tile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(tile.color, tile.color.copy(alpha = 0.82f))))
            .border(3.dp, Inks.Wood, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tile.emoji, fontSize = 24.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            text = tile.label,
            color = Inks.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 14.sp,
        )
    }
}
