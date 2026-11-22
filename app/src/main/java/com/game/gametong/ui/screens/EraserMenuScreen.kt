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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.game.gametong.game.DeskSurface
import com.game.gametong.game.EraserType
import com.game.gametong.ui.EraserMode
import com.game.gametong.ui.components.FunButton
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.components.SelectChip
import com.game.gametong.ui.theme.Inks

@Composable
fun EraserMenuScreen(
    onBack: () -> Unit,
    onStart: (EraserMode, EraserType, EraserType, Boolean, DeskSurface) -> Unit,
) {
    var mode by remember { mutableStateOf(EraserMode.WRESTLING) }
    var playerType by remember { mutableStateOf(EraserType.STANDARD) }
    var aiType by remember { mutableStateOf(EraserType.JUMBO) }
    var twoPlayer by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf(DeskSurface.WOOD) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep, Inks.Kraft)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "지우개 따먹기", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "문방구 책상 위, 지우개 두 개로 노는 두 가지 놀이",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Inks.SoftInk,
                )

                ModeCard(
                    title = "② " + EraserMode.WRESTLING.title,
                    subtitle = EraserMode.WRESTLING.subtitle,
                    emoji = "🤼",
                    color = Inks.Blue,
                    selected = mode == EraserMode.WRESTLING,
                ) { mode = EraserMode.WRESTLING }

                ModeCard(
                    title = "① " + EraserMode.ALKKAEGI.title,
                    subtitle = EraserMode.ALKKAEGI.subtitle,
                    emoji = "🪂",
                    color = Inks.Red,
                    selected = mode == EraserMode.ALKKAEGI,
                ) { mode = EraserMode.ALKKAEGI }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column {
                        Text("내 지우개", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                        Spacer(Modifier.height(8.dp))
                        EraserTypePicker(selected = playerType, onSelect = { playerType = it })
                        Spacer(Modifier.height(6.dp))
                        Text(
                            playerType.blurb,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (twoPlayer) "상대 지우개 (2P)" else "상대 지우개 (AI)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Inks.Wood,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "2인 번갈아",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Inks.SoftInk,
                            )
                            Spacer(Modifier.width(4.dp))
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
                        Spacer(Modifier.height(6.dp))
                        EraserTypePicker(selected = aiType, onSelect = { aiType = it }, enabled = !twoPlayer)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (twoPlayer) "한 기기를 번갈아 넘기며 두 사람이 겨룹니다."
                            else "AI는 여러 각도를 직접 시뮬레이션해 가장 좋은 한 수를 고릅니다.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column {
                        Text(
                            "책상 표면",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Inks.Wood,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "표면 마찰에 따라 미끄러지는 거리와 회전이 달라집니다.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.SoftInk,
                        )
                        Spacer(Modifier.height(8.dp))
                        for (row in DeskSurface.entries.chunked(2)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                for (type in row) {
                                    SelectChip(
                                        label = type.label,
                                        selected = type == surface,
                                        color = surfaceAccent(type),
                                        modifier = Modifier.weight(1f),
                                    ) { surface = type }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            surface.hint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Inks.Ink,
                        )
                    }
                }

                PaperCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = 12.dp,
                    color = Color(0xFFFFF3D6),
                ) {
                    Text(
                        text = if (mode == EraserMode.WRESTLING) {
                            "규칙 — 번갈아 한 번씩 튕깁니다. 상대 지우개 위로 걸쳐 올라 멈추면 1카운트. " +
                                "완전히 덮지 않아도 되지만 비스듬히 걸치면 상대가 빠져나옵니다. " +
                                "너무 약하면 옆으로 흐르고, 너무 세면 넘어가 버립니다. " +
                                "카운트가 나도 처음 자리로 돌아가지 않고 그 자리에서 계속 이어집니다. 3카운트 승리."
                        } else {
                            "규칙 — 번갈아 한 번씩 튕겨 상대 지우개를 책상 밖으로 떨어뜨립니다. " +
                                "내 지우개가 먼저 떨어지면 그 라운드는 상대 승리. 3판 2선승."
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Inks.Ink,
                    )
                }

                FunButton(
                    text = "시작하기",
                    modifier = Modifier.fillMaxWidth(),
                    color = Inks.Green,
                    fontSize = 21,
                ) { onStart(mode, playerType, aiType, twoPlayer, surface) }

                Spacer(Modifier.height(26.dp))
            }
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    emoji: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f) else Inks.Paper)
            .border(if (selected) 4.dp else 2.dp, if (selected) color else Inks.Wood.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 30.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Inks.Ink)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Inks.SoftInk)
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text("선택", color = Inks.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun EraserTypePicker(
    selected: EraserType,
    onSelect: (EraserType) -> Unit,
    enabled: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (type in EraserType.entries) {
            val isSelected = type == selected
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSelected) Inks.Blue.copy(alpha = 0.14f) else Inks.Kraft.copy(alpha = 0.5f))
                    .border(
                        if (isSelected) 3.dp else 2.dp,
                        if (isSelected) Inks.Blue else Inks.Wood.copy(alpha = 0.35f),
                        shape,
                    )
                    .clickable(enabled = enabled, onClick = { onSelect(type) })
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(eraserEmoji(type), fontSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    type.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (enabled) Inks.Ink else Inks.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

fun eraserEmoji(type: EraserType): String = when (type) {
    EraserType.JUMBO -> "🟦"
    EraserType.STANDARD -> "🧽"
    EraserType.NIMBLE -> "🟩"
}

/** 표면별 강조색(책상 느낌). */
fun surfaceAccent(type: DeskSurface): Color = when (type) {
    DeskSurface.COATED -> Inks.Blue
    DeskSurface.WOOD -> Inks.WoodLight
    DeskSurface.PLYWOOD -> Inks.Orange
    DeskSurface.DUSTY -> Inks.Purple
}
