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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.game.gametong.ui.components.DemoNotice
import com.game.gametong.ui.components.PaperCard
import com.game.gametong.ui.components.ScreenHeader
import com.game.gametong.ui.theme.Inks

@Composable
fun SettingsScreen(
    aimAssist: Boolean,
    onAimAssistChange: (Boolean) -> Unit,
    haptics: Boolean,
    onHapticsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Inks.Kraft, Inks.KraftDeep)))) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "설정", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column {
                        Text("게임 설정", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                        Spacer(Modifier.height(6.dp))
                        SwitchRow(
                            title = "조준 보조",
                            desc = "드래그 중 지우개가 멈출 자리를 점선으로 미리 보여줍니다",
                            checked = aimAssist,
                            onChange = onAimAssistChange,
                        )
                        SwitchRow(
                            title = "진동 피드백",
                            desc = "튕길 때·카운트될 때 짧게 진동",
                            checked = haptics,
                            onChange = onHapticsChange,
                        )
                        SwitchRow(
                            title = "소리 / BGM",
                            desc = "다음 업데이트에서 추가됩니다",
                            checked = false,
                            onChange = {},
                            enabled = false,
                        )
                    }
                }

                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                    Column {
                        Text("이번 체험판에 담긴 것", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Inks.Wood)
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            "지우개 따먹기 — 알까기(낙하) 모드: 물리 튕기기, 책상 밖 낙하 판정, 3판 2선승",
                            "지우개 따먹기 — 레슬링 모드: 턴제 1회 튕기기, 올라타면 1카운트, 3카운트 승리",
                            "AI 대전 또는 2인 번갈아 하기, 지우개 3종(무게·마찰·탄성)",
                            "딱지치기·구슬치기·공기놀이 맛보기 미니 데모",
                            "뿌리기 명성 계산, 동네 랭킹 화면(예시 데이터)",
                        ).forEach { line ->
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Text("· ", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Inks.Red)
                                Text(line, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Inks.Ink)
                            }
                        }
                    }
                }

                DemoNotice(
                    "체험판 v0.1.0-demo — 명성·수집품·랭킹은 기기에 저장되지 않는 화면 예시입니다. " +
                        "친구와의 대전(P2P), 서버 랭킹은 다음 단계입니다."
                )

                Text(
                    text = "우리동네 게임통 · 추억의 동네놀이\nv0.1.0-demo (빌드 1)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Inks.SoftInk,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = if (enabled) Inks.Ink else Inks.Gray,
            )
            Text(desc, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Inks.SoftInk)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Inks.White,
                checkedTrackColor = Inks.Green,
                uncheckedThumbColor = Inks.White,
                uncheckedTrackColor = Inks.Gray,
            ),
        )
    }
}
