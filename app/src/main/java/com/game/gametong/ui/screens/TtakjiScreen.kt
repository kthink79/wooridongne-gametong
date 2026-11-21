package com.game.gametong.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.game.gametong.game.RoundMode
import com.game.gametong.game.TtakjiMaterial
import com.game.gametong.game.TtakjiSurface
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.OptionCard
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.SelectChip
import com.game.gametong.ui.components.SectionTitle
import com.game.gametong.ui.theme.Inks

/**
 * 딱지치기 허브 — 네모 딱지(2.5D 물리)와 동그란 딱지(3종 놀이)를 먼저 고르고,
 * 세부 규칙(노면·재질·상대)을 정한 뒤 바로 시작한다.
 */
@Composable
fun TtakjiScreen(
    onBack: () -> Unit,
    onStartSquare: (TtakjiSurface, TtakjiMaterial, Int, TtakjiMaterial, Int, Boolean) -> Unit,
    onStartRound: (RoundMode, Boolean) -> Unit,
) {
    var section by remember { mutableIntStateOf(0) } // 0 = 네모, 1 = 동그란
    var surface by remember { mutableStateOf(TtakjiSurface.MARU) }
    var myMaterial by remember { mutableStateOf(TtakjiMaterial.COLOR_PAPER) }
    var myThickness by remember { mutableIntStateOf(2) }
    var aiMaterial by remember { mutableStateOf(TtakjiMaterial.NEWSPAPER) }
    var aiThickness by remember { mutableIntStateOf(2) }
    var roundMode by remember { mutableStateOf(RoundMode.STICK) }
    var twoPlayer by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "딱지치기", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle("어떤 딱지로 놀까?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionCard(
                        title = "네모 딱지",
                        subtitle = "뒤집기 2.5D 물리 · 노면·피로도·부상",
                        emoji = "🟥",
                        selected = section == 0,
                        color = Inks.Red,
                        modifier = Modifier.weight(1f),
                    ) { section = 0 }
                    OptionCard(
                        title = "동그란 딱지",
                        subtitle = "붙여먹기 · 멀리 날리기 · 입바람",
                        emoji = "🟡",
                        selected = section == 1,
                        color = Inks.Gold,
                        modifier = Modifier.weight(1f),
                    ) { section = 1 }
                }

                if (section == 0) {
                    SquareSetup(
                        surface = surface,
                        onSurface = { surface = it },
                        myMaterial = myMaterial,
                        onMyMaterial = { myMaterial = it },
                        myThickness = myThickness,
                        onMyThickness = { myThickness = it },
                        aiMaterial = aiMaterial,
                        onAiMaterial = { aiMaterial = it },
                        aiThickness = aiThickness,
                        onAiThickness = { aiThickness = it },
                    )
                } else {
                    RoundSetup(
                        mode = roundMode,
                        onMode = { roundMode = it },
                    )
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (twoPlayer) "2인 번갈아 (한 기기)" else "AI와 대전",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.Wood,
                            )
                            Text(
                                if (twoPlayer) "두 사람이 번갈아 조작합니다."
                                else "AI가 각도를 계산해 칩니다.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Inks.SoftInk,
                            )
                        }
                        Switch(
                            checked = twoPlayer,
                            onCheckedChange = { twoPlayer = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Inks.White,
                                checkedTrackColor = Inks.Green,
                                uncheckedThumbColor = Inks.White,
                                uncheckedTrackColor = Inks.Gray,
                            ),
                        )
                    }
                }

                FunButton(
                    text = if (section == 0) "네모 딱지치기 시작" else "${roundMode.title} 시작",
                    modifier = Modifier.fillMaxWidth(),
                    color = if (section == 0) Inks.Red else Inks.Gold,
                    textColor = if (section == 0) Inks.White else Inks.Ink,
                    fontSize = 20,
                ) {
                    if (section == 0) {
                        onStartSquare(surface, myMaterial, myThickness, aiMaterial, aiThickness, twoPlayer)
                    } else {
                        onStartRound(roundMode, twoPlayer)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SquareSetup(
    surface: TtakjiSurface,
    onSurface: (TtakjiSurface) -> Unit,
    myMaterial: TtakjiMaterial,
    onMyMaterial: (TtakjiMaterial) -> Unit,
    myThickness: Int,
    onMyThickness: (Int) -> Unit,
    aiMaterial: TtakjiMaterial,
    onAiMaterial: (TtakjiMaterial) -> Unit,
    aiThickness: Int,
    onAiThickness: (Int) -> Unit,
) {
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Column {
            Text("노면 고르기", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (item in TtakjiSurface.entries) {
                    SelectChip(
                        label = item.label,
                        selected = item == surface,
                        color = Inks.Blue,
                        modifier = Modifier.weight(1f),
                    ) { onSurface(item) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(surface.hint, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Inks.SoftInk)
        }
    }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Column {
            Text("내 딱지", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (item in TtakjiMaterial.entries) {
                    SelectChip(
                        label = item.label,
                        selected = item == myMaterial,
                        color = Inks.Red,
                        modifier = Modifier.weight(1f),
                    ) { onMyMaterial(item) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("두께", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (thickness in 1..3) {
                    SelectChip(
                        label = when (thickness) {
                            1 -> "얇게"
                            2 -> "보통"
                            else -> "두껍게"
                        },
                        selected = thickness == myThickness,
                        color = Inks.Red,
                        modifier = Modifier.weight(1f),
                    ) { onMyThickness(thickness) }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(myMaterial.blurb, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Inks.SoftInk)
        }
    }

    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Column {
            Text("상대 딱지", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (item in TtakjiMaterial.entries) {
                    SelectChip(
                        label = item.label,
                        selected = item == aiMaterial,
                        color = Inks.Blue,
                        modifier = Modifier.weight(1f),
                    ) { onAiMaterial(item) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (thickness in 1..3) {
                    SelectChip(
                        label = if (thickness == 1) "얇게" else if (thickness == 2) "보통" else "두껍게",
                        selected = thickness == aiThickness,
                        color = Inks.Blue,
                        modifier = Modifier.weight(1f),
                    ) { onAiThickness(thickness) }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "상대 딱지에는 들뜬 날개 틈이 보입니다. 그 틈을 옆에서 때리면 훨씬 잘 뒤집힙니다.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Inks.SoftInk,
            )
        }
    }
}

@Composable
private fun RoundSetup(
    mode: RoundMode,
    onMode: (RoundMode) -> Unit,
) {
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("동그란 딱지 3종", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
            for (item in RoundMode.entries) {
                OptionCard(
                    title = item.title,
                    subtitle = item.subtitle,
                    emoji = item.emoji,
                    selected = item == mode,
                    color = Inks.Gold,
                    modifier = Modifier.fillMaxWidth(),
                ) { onMode(item) }
            }
        }
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
}

/** 공용: 두툼한 스위치 줄(필요하면 다른 화면에서도). */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    color: Color = Inks.Green,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Inks.Paper)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
            Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Inks.SoftInk)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Inks.White,
                checkedTrackColor = color,
                uncheckedThumbColor = Inks.White,
                uncheckedTrackColor = Inks.Gray,
            ),
        )
    }
}
